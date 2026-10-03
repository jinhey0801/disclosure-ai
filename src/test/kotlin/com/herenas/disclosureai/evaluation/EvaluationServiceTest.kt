package com.herenas.disclosureai.evaluation

import com.herenas.disclosureai.evaluation.EvaluationService.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class EvaluationServiceTest {

	private fun classify(truth: Long, extracted: Long, others: List<Pair<String, Long>> = emptyList()) =
		EvaluationService.classify(
			BigDecimal.valueOf(truth), BigDecimal.valueOf(extracted), others.map { it.first to BigDecimal.valueOf(it.second) },
		)

	@Test
	fun `부호만 다르면 SIGN`() {
		assertThat(classify(-1000, 1000)).isEqualTo(ErrorType.SIGN)
	}

	@Test
	fun `천배 백만배 차이는 UNIT_SCALE`() {
		assertThat(classify(1_234_000, 1_234)).isEqualTo(ErrorType.UNIT_SCALE)
		assertThat(classify(1_234, 1_234_000_000)).isEqualTo(ErrorType.UNIT_SCALE)
	}

	@Test
	fun `반올림된 입력에서 단위를 빼먹어도 UNIT_SCALE`() {
		// 1,234,567,890원을 백만원 표기(1,235)로 읽고 원으로 착각한 경우
		assertThat(classify(1_234_567_890L, 1_235)).isEqualTo(ErrorType.UNIT_SCALE)
	}

	@Test
	fun `다른 기간 구분의 정답과 같으면 PERIOD_SCOPE_SWAP`() {
		// 3개월치 자리에 누적치를 넣은 경우
		assertThat(classify(300, 900, listOf("scope" to 900L))).isEqualTo(ErrorType.PERIOD_SCOPE_SWAP)
	}

	@Test
	fun `연결과 별도를 바꿔 넣으면 FS_DIV_SWAP`() {
		assertThat(classify(500, 450, listOf("fsDiv" to 450L))).isEqualTo(ErrorType.FS_DIV_SWAP)
	}

	@Test
	fun `그 밖에는 VALUE`() {
		assertThat(classify(500, 499)).isEqualTo(ErrorType.VALUE)
	}
}
