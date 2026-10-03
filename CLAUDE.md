# 프로젝트: 피투자사 보고자료 AI 구조화 서비스
- 목표: 공시 문서를 LLM으로 읽어 메타데이터 기준으로 구조화, 검증, 일괄 처리
- 스택: Java 17, Spring Boot 3, Spring AI(Anthropic), Spring Batch, MySQL, Docker, EC2
- 기간: 2주. RAG, Security, Kafka는 범위 밖
- 규칙: 기능 하나 끝날 때마다 내가 이해할 수 있게 설계 이유를 짧게 설명할 것

## 실행
- 전체 기동: `docker compose up --build` → http://localhost:8080/actuator/health
- 로컬 개발: IDE에서 `DisclosureAiApplication` 실행 (또는 `./gradlew bootRun`). DB는 NAS MySQL. 접속 정보는 프로젝트 루트 `.env`(git 제외)에 있고 `application.yml`이 자동으로 읽는다.
- 스키마 변경은 `src/main/resources/db/migration/V{n}__설명.sql` (Flyway). JPA `ddl-auto`는 `validate`.
- DART 정답 수집: `POST /api/admin/collect` (전체 18개사) 또는 `POST /api/admin/collect/{corpCode}`. `DART_API_KEY`는 `.env`에 둔다.
- 평가셋 기업은 Flyway `V4__seed_evaluation_companies.sql`에서 관리

## NAS (Synology DS220+)
- 접속: `ssh nas` (키 인증, `~/.ssh/config` 별칭). docker는 `/usr/local/bin/docker` 로 호출 (비대화형 셸 PATH에 없음)
- 배포 디렉터리: `/volume1/docker/disclosure-ai/` (compose 원본은 `deploy/nas/docker-compose.yml`, `.env`는 NAS에만 존재)
- MySQL 8.4: 3306 포트. 3307은 시놀로지 MariaDB가 사용 중
- 배포: main push → GitHub Actions(테스트 → ghcr.io/jinhey0801/disclosure-ai:latest) → NAS `disclosure-ai-watchtower`가 1분 주기로 app만 교체. 앱 주소 http://192.168.0.9:8080
- compose 변경은 자동 배포되지 않음: 수정 후 NAS에 복사하고 `docker compose up -d` 수동 실행
- 기존 watchtower가 돌고 있으므로 DB 컨테이너는 `com.centurylinklabs.watchtower.enable=false` 라벨로 제외
