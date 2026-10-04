# Green Action (BE 프로토타입 정리)

설문 → Green Profile → AI 추천 챌린지(주 3개) → 체크/대중교통 인증 → 포인트 → 캐릭터 진화.
AI 서버 API 명세는 AI 저장소의 `GreenAction_API_spec_for_BE_FE.md` 참고.

## API (모두 로그인 필요)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/green/survey` | 설문 저장 → AI `/api/profile` 호출 → 프로필(GSTI) 저장. 알 수 없는 필드는 버리고, 비어 있으면 400. **설문은 처음 한 번만**: 이미 프로필이 있으면 409 |
| GET | `/green/profile` | 내 프로필(GSTI). 설문 전이면 404. **하루 1회 자동 재계산**(아래 참고) |
| GET | `/green/challenges` | 이번 주(월요일 시작, KST) 챌린지 3개. 없으면 AI `/api/challenges/recommend`로 부여. 설문 전이면 409. 지난주 완료분은 제외하고 추천 |
| POST | `/green/challenges/{id}/check-in` | 자율 체크(하루 1회). 대중교통 챌린지는 400, 완료된 건 409, 오늘 이미 체크했으면 409, **직접 체크 한도를 넘으면 409**, 무구매 챌린지는 그날 쇼핑 영수증이 있으면 409 |
| POST | `/green/challenges/{id}/verify` | **사진 인증**(multipart `images` 1~3장). 통과/거절은 모두 200(`verified`), 사진 문제 400, AI 불가 503 |

오류 응답은 `{"message": "..."}` (GreenActionController의 `@ExceptionHandler`).

체크인 응답 `CheckInResponse`: `challenge`, `justCompleted`, `pointsAwarded`, `totalPoints`, `previousLevel`, `level`, `leveledUp`.

## 인증 방식 (하이브리드: 체크하기 + 인증하기)

챌린지마다 AI 카탈로그의 인증 방식을 부여 시점에 스냅샷으로 저장한다 (`UserChallenge.photoVerification`, `selfCheckLimit`, `crossCheck`).

- **직접 체크**: 하루 1회. 사진 인증이 있는 챌린지(`CAFE_*`, `FOOD_1/2`)는 주간 인정 횟수에 한도가 있다 (텀블러: 목표의 절반 내림 / 저탄소 마크: 절반 올림). 한도를 넘으면 "인증하기"로 안내.
- **사진 인증** (`ChallengeService.verify` → AI `/api/challenges/verify`): 텀블러+카페 영수증, 저탄소 마크+영수증. 인증 횟수는 **무제한**, 같은 영수증은 한 번만(`challenge_verifications.fingerprint` 유니크: 날짜·시각·금액 해시). 사진은 저장하지 않는다.
  - 인증하면 진행도 +1(완료 시 완료 포인트), 인증 보너스 +5P(하루 3번까지, `VERIFY_BONUS_POINTS` / `VERIFY_BONUS_DAILY_CAP`). 완료한 챌린지도 추가 인증 가능(보너스만).
  - 거절(영수증 못 읽음·오래됨·카페 아님·텀블러/마크 없음·중복)은 진행도·포인트·지문 소모가 없다.
- **무구매 교차 검증** (`SHOP_2/3`, `crossCheck=NO_SHOPPING_RECEIPT`): 그날 확정된 "쇼핑소비재" 영수증(`consumption_records.ocr_data`)이 있으면 체크를 거절하고, 체크한 날에 영수증이 뒤늦게 등록되면 챌린지 조회 때 그 체크를 취소한다.

## 이동 인증의 속도 검증 (대중교통/도보)

`POST /dashboard/certify-transit`은 이제 앱이 보낸 절감량·포인트를 믿지 않는다. 시각이 있는 경로(`route[].timestamp/speed/accuracy/segmentBreak`)를 `TransitTripAnalyzer`로 분석해 이동수단 주장을 검증하고, 인정하는 거리와 절감량·포인트(자차 0.192 − 수단별 계수, 50P/kg)를 서버가 다시 계산한다.

- 인정하지 않으면 `success=false` + `message`(사유)로 200을 돌려주고 활동을 기록하지 않는다. 인정된 이동만 서버가 계산한 거리로 대중교통 챌린지에 반영된다.
- 기준: 도보는 상위 5% 속도 15km/h 이하·차량 속도 구간 30% 미만, 대중교통은 이동 시간 50% 이상이 12km/h 이상 또는 GPS 끊김 후 점프(지하철) 1km 이상, 순간 200km/h 초과·30분 지난 경로·일시정지 후 재개 지점의 점프는 거절. 기준값은 잠정값이다.
- **속도만으로 버스와 자가용은 구분할 수 없다.** 명백한 불일치(걸으면서 대중교통, 차로 도보)만 거른다. 모의 위치 앱이나 요청 직접 조작은 막지 못한다.
- 경로가 없는 예전 앱의 도보/대중교통 요청은 거절된다 (자차는 절감량 0이라 허용).

근거 자료와 검증 상태는 AI 저장소의 `GreenAction_verification_evidence.md`.

## GSTI 자동 갱신 (설문은 한 번만)

- 앱 문구는 "그린 유형"이 아니라 **GSTI(Green Step Type Indicator)**. "MBTI"라는 말은 상표 문제로 쓰지 않는다.
- 설문을 다시 하면 형평성이 어긋나므로 `POST /green/survey`는 처음 한 번만 허용한다(409 + 안내). 로컬 테스트에서만 `GREEN_SURVEY_ALLOW_RETAKE=true`로 풀 수 있다(`green.survey.allow-retake`).
- 이후 GSTI는 `getMyProfile`과 새 주 챌린지 부여 때, 마지막 갱신이 어제 이전이면 **저장된 설문 답 + `user_id` + 최근 30일 챌린지 완료 수(`recent_challenge_completions`)**로 AI `/api/profile`을 다시 호출해 갱신한다. 앞 세 축(이동·식탁·소비)은 영수증·소비 데이터에서, 태도 축은 챌린지 완료 이력(2개 이상이면 실행가)에서 바뀐다. AI 호출이 실패하면 기존 프로필을 그대로 쓴다.
- 유형이 바뀌면 프로필 JSON에 `type_changed_from`, `type_changed_from_name`, `type_changed_at`을 7일간 담아 화면이 "GSTI가 바뀌었어요" 안내를 보여 준다.
- 챌린지 추천 요청에는 `user_name`(이름, 없으면 닉네임)을 함께 보낸다. 추천 이유 문구는 GSTI 유형 이름 대신 "OO님"으로 부른다.

## 테이블 (ddl-auto: update 로 생성)

- `challenge_verifications` — 사진 인증 기록. 사진은 없고 영수증 지문(유니크), 가맹점·결제일·금액, 근거, 보너스 포인트만 남긴다.
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

## 가정 에너지(관리비): 직접 입력 + 고지서 사진 읽기

전기·수도·도시가스·지역난방의 월간 사용량/금액을 기록하고 탄소를 계산한다. 근거 자료와 한계는 AI 저장소 `GreenAction_household_evidence.md`.

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/household/bills/read` | 고지서 사진(multipart `images` 1~3장)을 AI로 읽어 사용월·값을 돌려준다(저장하지 않음, 사진도 저장 안 함). 읽으면 `readId`를 준다 |
| POST | `/household/bills` | 한 달 저장(월당 1건, 덮어쓰기). 탄소 계산(AI)과 절감 포인트 지급까지 처리 |
| GET | `/household/bills` | 내 기록(최근 달부터): 항목별 탄소, 인증 상태, 작년 같은 달 대비 비교, 받은 포인트 |

- **고지서 인증**: 저장 때 `readId`를 보내면 서버가 입력값이 읽은 값과 같은 항목만 "인증"으로 기록한다(앱이 인증됐다고 주장해도 믿지 않음). 한 항목이라도 고치면 그 항목은 직접 입력이 된다. 읽기 결과는 2시간, 같은 사용자·같은 달에만 유효하다. 같은 고지서(월 + 읽은 값 해시)는 다른 계정에서 인증에 쓸 수 없다.
- **절감 포인트**(`HouseholdSavings`): 환경부 탄소중립포인트제처럼 작년 같은 달보다 5·10·15% 이상 줄이면 1·2·3단계(전기 40/80/120P, 가스·지역난방 30/60/90P, 수도 10/20/30P). **이번 달과 작년 같은 달이 모두 고지서로 인증된 경우에만** 지급하고, 최근 3개월 평균은 계절 영향 때문에 참고로만 보여 준다. 이미 받은 단계는 다시 주지 않고 올라간 차액만 준다. 보상은 `EcoCategory.HOUSEHOLD` 활동으로 기록하며(인증 화면 목록에서 숨김, 직접 인증 불가) 포인트는 캐릭터 레벨업 조건에 들어간다. 이 보상은 **포인트만** 주고 절감 탄소량(savedCarbon)은 기록하지 않는다(계절·가구 변화 때문에 측정값으로 보기 어려워서).
- 입력 범위: 이번 달부터 최근 13개월(작년 같은 달을 올릴 수 있는 범위), 전기 3000kWh·수도 300㎥·가스 1000㎥·난방 40Gcal·금액 200만원 이하.
- 테이블: `household_bills`(월당 1건: 값, 인증 항목, 탄소, 포인트 지급 상태, 고지서 지문), `household_bill_reads`(읽기 결과 임시 기록).

## 배포 시 주의

- `common/config/EcoCategoryConstraintSync`: Hibernate가 만든 `eco_activities_category_check` 제약은 enum에 값이 추가돼도 갱신되지 않아서(CHALLENGE 추가 시 INSERT 500), 서버 기동 때 현재 enum 값으로 제약을 다시 만든다. 여러 번 실행해도 안전하고, 실패해도 기동은 막지 않는다.
- AI 서버 주소는 `AI_SERVICE_URL` (기본 `http://localhost:8000`). 배포 환경에서 BE가 AI에 접근할 수 있어야 한다.

## 로컬 검증

`CharacterLevelTest`(하이브리드 경계값)로 단위 테스트, BE+AI+DB를 띄운 통합 시나리오(관리비 34개 항목 별도 통과 / 설문→재설문 거절→추천→체크→포인트→레벨업→주 전환→사진 인증/중복/보너스 한도→무구매 교차 검증→이동 속도 검증→GSTI 자동 갱신)는 78개 항목 모두 통과. 사진 인증은 실제 Gemini로 합성 영수증을 읽어 확인했고, 이동 속도 검증은 합성 경로(`TransitTripAnalyzerTest` 13개)로 확인했다. 실제 텀블러·마크 사진과 실제 이동 데이터로는 아직 확인하지 못했다.
