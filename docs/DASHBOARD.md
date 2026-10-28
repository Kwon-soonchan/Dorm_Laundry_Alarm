# Laundry Lab 대시보드

## 바로 사용하기

- 화면 체험: https://dorm-laundry-lab.clover9593.chatgpt.site (소유자 비공개)
- 실제 수집: 프로젝트 폴더에서 `.\scripts\run-local.ps1` 실행 후 `http://localhost:8081`
- 휴대폰: PC와 같은 네트워크에 연결하고 `http://PC의-IP주소:8081`

웹훅도 시험하려면 실행 명령에 `-PromptForWebhook`을 추가합니다. 로컬 모드에는 Java 21, MySQL, Mosquitto가 필요합니다. 온라인 데모가 PC의 로컬 서버에 자동 연결되지는 않습니다.

## 화면과 데이터

- 5초마다 현재 기기 상태, 최근 수신 시각, 마지막 감지 비율과 저장 건수를 조회합니다.
- 시간 범위는 최근 5분, 15분, 1시간이며 조회당 최대 5,000개 구간을 표시합니다.
- 마지막 텔레메트리가 없으면 수신 대기, 15초 넘게 오래되면 연결 확인 필요로 표시합니다.
- 연결 중단이나 서버 요청 실패 상태에서는 세탁 완료를 표시하지 않습니다.
- 그래프에서 5초 넘는 수신 공백은 선을 이어 그리지 않습니다.
- 감지 비율은 SW-420의 HIGH 샘플 수 / 전체 샘플 수입니다. 물리적인 진동 세기가 아닙니다.
- 서버 API의 시각은 UTC ISO 8601이며 화면에서는 기기의 현지 시간으로 표시합니다.

## 실험 기록 흐름

1. 기기 번호를 선택하고 새 실험에 제목을 입력합니다.
2. 시작 전 정지 상태를 2~3분 수집합니다.
3. 본세탁·헹굼·탈수 버튼과 메모로 관찰 시각을 기록합니다.
4. 관찰한 종료 시각에 **실제 세탁 종료**를 누릅니다.
5. 5분 더 수집하고 **실험 수집 종료**를 누릅니다.
6. **실험 진동 CSV**와 **관찰 기록 CSV**를 각각 내려받아 시각 기준으로 비교합니다.

실험 시작·수집 종료 버튼은 센서 자체를 켜거나 끄지 않습니다. 서버에 계속 쌓이는 텔레메트리에서 비교할 시간 범위를 정합니다. 같은 기기에서는 한 번에 하나의 실험만 시작할 수 있으며, 종료된 실험의 기록은 수정하지 않습니다. 최근 50개 실험을 조회합니다.

## 저장 위치

실시간 기록은 MySQL의 `experiment`, `experiment_event`에 저장됩니다. 서버 재시작이나 다른 휴대폰 접속 후에도 유지됩니다. 진동 데이터는 기존 `vibration_telemetry`를 사용합니다.

데모 기록은 이 브라우저의 localStorage에만 저장하며 실제 DB·Discord에 접근하지 않습니다. 데모 CSV에는 `DEMO` 출처를 포함합니다. 데모 진동은 현재 페이지에서 생성한 최근 1시간만 유지하므로 과거 데모 실험의 진동 CSV는 비어 있을 수 있습니다.

## API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/dashboard?machineId=1&minutes=15` | 상태와 최근 진동 구간 |
| GET | `/api/experiments?machineId=1` | 최근 50개 실험 |
| POST | `/api/experiments` | `{"title":"표준 코스","machineId":1}` |
| GET | `/api/experiments/{id}/events` | 관찰 기록 |
| POST | `/api/experiments/{id}/events` | `{"kind":"SPIN","note":"탈수 시작"}` |
| POST | `/api/experiments/{id}/finish` | 실험 수집 범위 종료 |
| GET | `/api/experiments/{id}/events.csv` | 관찰 기록 CSV |
| GET | `/api/experiments/{id}/telemetry.csv` | 해당 기기·실험 시간 범위의 진동 CSV |
| GET | `/api/telemetry/export.csv?machineId=1` | 해당 기기의 전체 진동 CSV |

`kind`는 `WASH`, `RINSE`, `SPIN`, `FINISH`, `NOTE` 중 하나입니다. `START`는 실험 시작 때 자동 생성합니다. 기기 번호는 펌웨어 MQTT 토픽의 마지막 숫자와 맞춰야 합니다.

## 검증

`.\gradlew.bat test`는 H2 임시 DB로 수신 대기·장기 미수신·실시간 상태, 기록 생성, 잘못된 입력, 실험 종료 후 기록 차단, CSV 구간·기기 필터를 검증합니다. 테스트는 MQTT나 실제 MySQL·웹훅을 사용하지 않습니다. 한글 경로에서 Gradle 테스트 워커가 클래스를 찾지 못하면 영문 경로의 복사본에서 실행하세요.

Playwright와 Chrome이 설치된 환경에서는 `node scripts/check-dashboard.cjs`로 브라우저 동작과 390px·1440px 레이아웃을 검증할 수 있습니다. 결과 스크린샷은 `build/dashboard-qa/`에 생성합니다.

현재 화면은 임시 완료 판정 결과를 보여줍니다. 실제 세탁 종료 정확도는 허가된 세탁기 데이터로 별도 검증해야 합니다.
