package com.herenas.disclosureai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.herenas.disclosureai.evaluation.EvaluationService.ErrorType;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvaluationServiceTest {

	private static ErrorType classify(long truth, long extracted, Map<String, BigDecimal> others) {
		return EvaluationService.classify(BigDecimal.valueOf(truth), BigDecimal.valueOf(extracted), others);
	}

	@Test
	void 부호만_다르면_SIGN() {
		assertThat(classify(-1000, 1000, Map.of())).isEqualTo(ErrorType.SIGN);
	}

	@Test
	void 천배_백만배_차이는_UNIT_SCALE() {
		assertThat(classify(1_234_000, 1_234, Map.of())).isEqualTo(ErrorType.UNIT_SCALE);
		assertThat(classify(1_234, 1_234_000_000, Map.of())).isEqualTo(ErrorType.UNIT_SCALE);
	}

	@Test
	void 반올림된_입력에서_단위를_빼먹어도_UNIT_SCALE() {
		// 1,234,567,890원을 백만원 표기(1,235)로 읽고 원으로 착각한 경우
		assertThat(classify(1_234_567_890L, 1_235, Map.of())).isEqualTo(ErrorType.UNIT_SCALE);
	}

	@Test
	void 다른_기간_구분의_정답과_같으면_PERIOD_SCOPE_SWAP() {
		// 3개월치 자리에 누적치를 넣은 경우
		assertThat(classify(300, 900, Map.of("scope:0", BigDecimal.valueOf(900)))).isEqualTo(ErrorType.PERIOD_SCOPE_SWAP);
	}

	@Test
	void 연결과_별도를_바꿔_넣으면_FS_DIV_SWAP() {
		assertThat(classify(500, 450, Map.of("fsDiv:0", BigDecimal.valueOf(450)))).isEqualTo(ErrorType.FS_DIV_SWAP);
	}

	@Test
	void 그_밖에는_VALUE() {
		assertThat(classify(500, 499, Map.of())).isEqualTo(ErrorType.VALUE);
	}
}
