# Green Action (BE 프로토타입 정리)

설문 → Green Profile → AI 추천 챌린지(주 3개) → 체크/대중교통 인증 → 포인트 → 캐릭터 진화.
AI 서버 API 명세는 AI 저장소의 `GreenAction_API_spec_for_BE_FE.md` 참고.

## API (모두 로그인 필요)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/green/survey` | 설문 저장 → AI `/api/profile` 호출 → 프로필 저장. 알 수 없는 필드는 버리고, 비어 있으면 400 |
| GET | `/green/profile` | 저장된 프로필. 설문 전이면 404 |
| GET | `/green/challenges` | 이번 주(월요일 시작, KST) 챌린지 3개. 없으면 AI `/api/challenges/recommend`로 부여. 설문 전이면 409. 지난주 완료분은 제외하고 추천 |
| POST | `/green/challenges/{id}/check-in` | 자율 체크(하루 1회). 대중교통 챌린지는 400, 완료된 건 409, 오늘 이미 체크했으면 409 |

오류 응답은 `{"message": "..."}` (GreenActionController의 `@ExceptionHandler`).

체크인 응답 `CheckInResponse`: `challenge`, `justCompleted`, `pointsAwarded`, `totalPoints`, `previousLevel`, `level`, `leveledUp`.

## 테이블 (ddl-auto: update 로 생성)

- `green_profiles` — 사용자당 1행. 설문 답변/프로필 JSON, 출처(survey/data), 유형 코드
- `user_challenges` — 사용자에게 부여한 챌린지. **AI가 준 카탈로그 내용을 그대로 스냅샷으로 저장**(카탈로그 seed 테이블 없음). 상태 ACTIVE/COMPLETED, 주 시작일, 진행 횟수
- `challenge_check_ins` — 체크 기록. (user_challenge_id, check_date) 유니크로 하루 1회 보장. 방법 MANUAL / AUTO_TRANSIT

## 포인트와 캐릭터 레벨

- 챌린지 완료 시 `EcoActivity`(카테고리 `CHALLENGE`)를 1건 남긴다. 그래서 기존 포인트 합계, 월간 랭킹, 내 활동 목록에 그대로 반영된다. 절감량은 예상치라 0으로 기록한다.
- `CHALLENGE` 카테고리는 인증 화면 카테고리 목록에 나오지 않고, 직접 인증하면 400.
- 대중교통 GPS 인증(`DashboardController`의 certifyTransit 이후)이 들어오면 이번 주 대중교통 챌린지에 하루 1회(0.3km 이상, mode=TRANSIT) 자동 반영.
- **캐릭터 레벨업은 하이브리드**: 누적 절감 탄소와 누적 포인트를 *둘 다* 채워야 올라간다 (`CharacterLevel`).

| 레벨 | 누적 절감 | 누적 포인트 (잠정) |
|---|---|---|
| Lv.2 | 10kg | 600P |
| Lv.3 | 30kg | 1,800P |
| Lv.4 | 60kg | 3,600P |

  포인트 기준은 활동 인증이 약 50P/kg(예: 텀블러 2.0kg/100P)인 점을 감안해 "탄소 기준 × 50P + 챌린지 보너스(100/300/600P)"로 잡은 잠정값이다. 프론트 `src/constants/levels.js`와 반드시 같은 값으로 유지한다.
- 레벨은 저장하지 않고 요청 때마다 합계로 계산한다 (활동 요약, 내 정보, 인증 응답, 체크인 응답 모두 같은 규칙).

## 배포 시 주의

- `common/config/EcoCategoryConstraintSync`: Hibernate가 만든 `eco_activities_category_check` 제약은 enum에 값이 추가돼도 갱신되지 않아서(CHALLENGE 추가 시 INSERT 500), 서버 기동 때 현재 enum 값으로 제약을 다시 만든다. 여러 번 실행해도 안전하고, 실패해도 기동은 막지 않는다.
- AI 서버 주소는 `AI_SERVICE_URL` (기본 `http://localhost:8000`). 배포 환경에서 BE가 AI에 접근할 수 있어야 한다.

## 로컬 검증

`CharacterLevelTest`(하이브리드 경계값)로 단위 테스트, BE+AI+DB를 띄운 통합 시나리오(설문→추천→체크→포인트→레벨업→주 전환)는 38개 항목 모두 통과.
