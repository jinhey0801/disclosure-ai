package com.herenas.disclosureai.domain.common;

/** 재무제표 구분. 코드값은 DART API(fs_div)와 동일하다. */
public enum FsDiv {
	/** 연결재무제표 */
	CFS,
	/** 별도재무제표 */
	OFS,
	/** 재무제표와 무관한 항목 (임직원 수 등) */
	NONE
}
