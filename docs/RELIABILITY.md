# 재시작·연결 장애·알림 복구

## 적용 범위

기존의 무진동 시간 기반 완료 기준을 유지합니다. 로컬 10초, 기본 180초 값은 변경하지 않았으며 실제 세탁 데이터로 보정해야 합니다. 이번 변경은 수집과 알림 전달 과정의 장애 처리입니다.

## 상태 복구와 통신 공백

`machine_monitor`에 MQTT 기기 ID, RUNNING/AVAILABLE, 세탁 주기 UUID, 마지막 진동·상태 수신 시각, 연속 무진동 시작 시각을 저장합니다. 완료 상태와 `notification_outbox`의 알림 1건을 같은 DB 트랜잭션에서 기록합니다.

- 정상적으로 상태가 계속 수신되면 기존 마지막 진동 시각부터 임계값을 계산합니다.
- 서버 재시작, 상태 메시지 15초 초과 공백, 장치 online/offline 또는 브로커 연결 실패를 감지하면 기존 무진동 구간을 무효화합니다.
- 복구 후 첫 0부터 새 연속 관측 구간을 확보합니다. 공백 시간은 무진동으로 채우지 않습니다.
- 새 1이 오면 진행 중인 주기는 유지하고 마지막 진동 시각을 갱신합니다. AVAILABLE 상태의 새 1은 새 주기를 만듭니다.
- 과거 retained 진동·텔레메트리는 무시하며, retained 연결 상태는 복구 판단에 사용합니다.
- 기존 `machine`의 RUNNING 상태는 첫 메시지에서 보수적으로 이어받습니다. 과거에 저장하지 않았던 진동 시각은 추측하지 않습니다.

상태 gap 설정은 펌웨어의 5초 상태 보고 주기보다 충분히 길어야 합니다. 서버 중단 동안 세탁이 끝나고 다음 세탁이 시작된 경우처럼 관측 자체가 없는 구간은 복원할 수 없습니다.

## 펌웨어 적용

`firmware/laundry_sensor/laundry_sensor.ino`를 NodeMCU에 다시 업로드해야 연결 상태 알림까지 적용됩니다. 작업 폴더의 기존 `laundry_1/laundry_1.ino`에도 동일한 연결 처리 변경을 반영했습니다. 실제 `secrets.h`는 변경하지 않았습니다.

- 연결 성공 시 `laundry/status/1 = online`을 retained로 발행합니다.
- MQTT Last Will은 같은 토픽에 `offline`을 발행합니다. 감지에는 keepalive에 따른 지연이 있습니다.
- 재연결 직후 현재 센서 상태를 다시 발행합니다.
- 재연결 시 텔레메트리 집계를 초기화하여 연결 중단 시간을 수집 창에 포함하지 않습니다.
- 전송 실패 시 소켓을 닫고 재연결합니다. 오프라인 데이터의 재전송·복원 기능은 없습니다.

다른 기기를 추가할 때 vibration, telemetry, status 토픽의 번호를 함께 바꾸고 서버 구독 범위도 맞춰야 합니다. 현재 기본 데이터 구독은 1번 기기입니다.

## 알림 발송 정책

각 세탁 주기의 UUID가 알림의 DB 기본키이자 Discord 메시지의 참조 번호입니다. DB 행 잠금과 발송 상태를 사용해 같은 이벤트를 동시 발송하거나 이미 성공한 이벤트를 다시 보내지 않습니다.

| 상태 | 의미 및 처리 |
| --- | --- |
| PENDING | 발송 대기 또는 재시도 시각 대기. 웹훅 미설정 시 그대로 보관 |
| SENDING | 한 작업자가 발송 중. 다른 작업자는 가져가지 않음 |
| SENT | Discord 메시지 ID로 발송 확인. 자동·수동 재발송 차단 |
| FAILED | 설정/요청 오류 또는 재시도 횟수 소진. 원인 수정 후 수동 재시도 |
| UNCERTAIN | 응답 유실, 5xx, 확인 ID 없음, 발송 중 프로세스 중단. Discord에서 수신 여부 확인 필요 |

연결 수립 실패와 429는 자동 재시도합니다. 기본 지연은 5·10·20초처럼 증가하며 최대 300초, 최대 8회입니다. 429의 `Retry-After` 헤더와 `retry_after` 본문을 반영하고 더 긴 대기 시간을 따릅니다. 웹훅 전체 대기 시각도 DB에 저장하므로 다른 알림이나 서버 재시작이 발송 제한을 우회하지 않습니다.

Discord 요청은 `wait=true`로 메시지 ID를 확인합니다. 연결 제한은 3초, 응답 제한은 5초입니다. 발송 중 프로세스가 중단되어 SENDING 상태가 60초 넘게 남으면 UNCERTAIN으로 전환합니다.

외부 서비스가 메시지를 저장한 직후 응답이 사라지는 경우에는 자동으로 발송 여부를 확정할 수 없습니다. 따라서 모든 상황에서 정확히 한 번 전송된다고 보장하지 않습니다. 불확실한 요청은 자동 재발송을 보류해 중복을 줄이며, 사용자가 미수신을 확인한 경우에만 수동 재시도합니다.

Discord 정책 참고: [웹훅 실행과 wait](https://github.com/discord/discord-api-docs/blob/main/developers/resources/webhook.mdx#execute-webhook), [발송 제한과 재시도 대기](https://github.com/discord/discord-api-docs/blob/main/developers/topics/rate-limits.mdx).

## 상태 조회·수동 복구

로컬 서버 실행 중 PowerShell에서 조회합니다. 웹훅 URL·토큰은 응답이나 오류 코드에 포함하지 않습니다.

```powershell
Invoke-RestMethod 'http://localhost:8081/api/notifications?machineId=1' |
    ConvertTo-Json -Depth 5
```

FAILED는 오류 원인을 먼저 수정합니다. UNCERTAIN은 Discord 채널에서 메시지의 참조 번호를 찾아 실제 미수신 여부를 확인합니다. 미수신이 확인된 이벤트에만 다음 요청을 실행합니다.

```powershell
$notificationId = '조회한-이벤트-UUID'
Invoke-RestMethod -Method Post `
    -Uri "http://localhost:8081/api/notifications/$notificationId/retry" `
    -ContentType 'application/json' `
    -Body '{"confirmedNoDelivery":true}'
```

수동 재시도는 해당 이벤트를 PENDING으로 돌리고 시도 횟수를 초기화합니다. 이미 SENT이거나 발송 중인 이벤트에는 409를 반환합니다. 원시 DB 상태를 직접 바꾸어 재발송하지 마세요.

## 실행과 검증

기존 `.\scripts\run-local.ps1 -PromptForWebhook` 명령으로 실행합니다. JPA가 `machine_monitor`, `notification_outbox`, `notification_dispatch_gate` 테이블을 생성합니다. 기존 텔레메트리와 실험 기록을 삭제하지 않습니다.

일반 테스트는 실제 MySQL·Discord 없이 H2 임시 DB와 로컬 가짜 HTTP 서버를 사용합니다.

```powershell
.\gradlew.bat test --no-daemon
```

Mosquitto가 설치되어 있으면 아래 명령으로 격리된 브로커를 강제 종료·재시작하는 전체 흐름까지 검증합니다. 테스트 소유의 임시 포트만 사용하며 운영 브로커는 종료하지 않습니다. 기본 Mosquitto 경로가 다르면 `MOSQUITTO_EXECUTABLE` 환경변수를 지정하세요.

```powershell
.\gradlew.bat test --no-daemon '-Dlaundry.mqtt.smoke=true'
```

한글 경로에서 Gradle 테스트 워커 문제가 있으면 영문 경로의 복사본에서 실행합니다. 펌웨어 컴파일은 예제 설정으로 검증하며 실제 장치에 자동 업로드하지 않습니다.

## 남은 현장 검증

이 구성은 로컬 서버 1개와 기존 MQTT 구성을 기준으로 합니다. 실제 설치 후에는 장치 전원 재인가, 현장 Wi-Fi 단절·복구, 장시간 수집량, 센서 감도와 완료 판정 정확도를 별도로 확인해야 합니다.
