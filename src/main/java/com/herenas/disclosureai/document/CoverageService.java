package com.herenas.disclosureai.document;

import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.document.ReportSection;
import com.herenas.disclosureai.domain.metric.StatementType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LLM 없이 하는 사전 점검: 정답 숫자가 LLM 에 넣을 본문 텍스트 안에 실제로 있는가.
 * 없으면 아무리 좋은 모델이라도 맞힐 수 없으므로, 추출 정확도를 보기 전에 입력 품질부터 확인한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoverageService {

	private final EntityManager em;

	public record Miss(String company, int fiscalYear, String reportType, FsDiv fsDiv, String metric,
			String periodScope, BigDecimal value, String reason) {
	}

	public record Coverage(int reportsWithSections, int truths, int found, int zeroValues, List<Miss> misses) {
	}

	private record Truth(Long reportId, String company, int fiscalYear, String reportType, FsDiv fsDiv,
			StatementType statementType, String metric, String periodScope, BigDecimal value) {
	}

	public Coverage check() {
		Map<String, String> textByKey = em.createQuery("select s from ReportSection s", ReportSection.class)
				.getResultList().stream()
				.collect(Collectors.groupingBy(s -> key(s.getReport().getId(), s.getFsDiv(), s.getStatementType()),
						Collectors.mapping(ReportSection::getContent, Collectors.joining("\n"))));
		long reportsWithSections = textByKey.keySet().stream().map(k -> k.split(":")[0]).distinct().count();

		List<Truth> truths = em.createQuery("""
				select g.report.id, g.report.company.corpName, g.report.fiscalYear, g.report.reportType, g.fsDiv,
				       g.metric.statementType, g.metric.code, g.periodScope, g.value
				from GroundTruth g
				""", Object[].class).getResultList().stream()
				.map(r -> new Truth((Long) r[0], (String) r[1], (Integer) r[2], r[3].toString(), (FsDiv) r[4],
						(StatementType) r[5], (String) r[6], r[7].toString(), (BigDecimal) r[8]))
				.toList();

		int found = 0;
		int zeros = 0;
		List<Miss> misses = new ArrayList<>();
		for (Truth t : truths) {
			String text = textByKey.get(key(t.reportId(), t.fsDiv(), t.statementType()));
			if (text == null) {
				misses.add(miss(t, "본문 표 없음"));
			} else if (t.value().signum() == 0) {
				zeros++; // 0 은 "0", "-", 빈칸 등 표기가 제각각이라 별도로 센다
			} else if (containsNumber(text, t.value())) {
				found++;
			} else {
				misses.add(miss(t, "본문에 숫자 없음"));
			}
		}
		return new Coverage((int) reportsWithSections, truths.size(), found, zeros, misses);
	}

	/** 원 단위 절댓값을 천 단위 콤마로 찾는다. 음수 표기((1,234), -1,234, △1,234)와 무관하게 하려고 절댓값만 본다. */
	public static boolean containsNumber(String text, BigDecimal value) {
		String formatted = new DecimalFormat("#,##0").format(value.abs());
		return Pattern.compile("(?<![\\d,])" + Pattern.quote(formatted) + "(?![\\d,])").matcher(text).find();
	}

	private static Miss miss(Truth t, String reason) {
		return new Miss(t.company(), t.fiscalYear(), t.reportType(), t.fsDiv(), t.metric(), t.periodScope(),
				t.value(), reason);
	}

	private static String key(Long reportId, FsDiv fsDiv, StatementType type) {
		return reportId + ":" + fsDiv + ":" + type;
	}
}
