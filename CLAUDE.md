# 프로젝트: 피투자사 보고자료 AI 구조화 서비스
- 목표: 공시 문서를 LLM으로 읽어 메타데이터 기준으로 구조화, 검증, 일괄 처리
- 스택: Java 17, Spring Boot 3, Spring AI(Anthropic), Spring Batch, MySQL, Docker, EC2
- 기간: 2주. RAG, Security, Kafka는 범위 밖
- 규칙: 기능 하나 끝날 때마다 내가 이해할 수 있게 설계 이유를 짧게 설명할 것

## 실행
- 전체 기동: `docker compose up --build` → http://localhost:8080/actuator/health
- 로컬 개발: `docker compose up -d mysql` 후 IDE에서 `DisclosureAiApplication` 실행
- 스키마 변경은 `src/main/resources/db/migration/V{n}__설명.sql` (Flyway). JPA `ddl-auto`는 `validate`.
