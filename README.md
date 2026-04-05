# 🧺 기숙사 세탁기 진동 감지 알리미 (Dorm Laundry Alarm)

공용 세탁기의 작동 완료 시점을 직관적으로 파악하기 위해, 하드웨어 센서로 진동을 감지하고 세탁이 끝났을 때 디스코드(Discord)로 푸시 알림을 보내주는 IoT 기반 토이 프로젝트입니다. 

## 🎯 Project Motivation
- 기숙사 공용 세탁기가 언제 끝날지 몰라 여러 번 확인하러 가야 하는 번거로움을 해결합니다.
- 하드웨어(센서 통신)부터 백엔드(데이터 처리 및 외부 API 연동)까지 데이터의 전체 흐름을 제어합니다.

## 🛠 Tech Stack

### Hardware & IoT
- **Board:** NodeMCU V3 (ESP8266)
- **Sensor:** SW-420 (진동 센서 모듈)
- **Protocol:** MQTT (Mosquitto Broker)

### Backend
- **Language:** Java 21
- **Framework:** Spring Boot 3.x
- **Database:** MySQL, Spring Data JPA
- **Notification:** Discord Webhook API

## 🏛 Architecture & Design Principles

1. **관심사의 분리 (Separation of Concerns):** - 하드웨어 데이터 수신(MQTT), 비즈니스 로직(진동 분석 및 세탁 상태 판별), 알림 발송(Discord)의 역할을 철저히 분리하여, 향후 웹 프론트엔드 연동이나 다른 알림 매체로의 확장이 유연하도록 설계했습니다.
2. **데이터 안정성:** - 데이터베이스의 모든 식별자(PK) 및 시간/수치 데이터는 Integer 오버플로우를 방지하기 위해 `Long`(`BIGINT`) 타입으로 설계하여 대용량 로그 적재 시의 안정성을 확보했습니다.
3. **상태 관리 및 로깅:**
   - 기기의 현재 상태를 관리하는 `MACHINE` 테이블과, 작동 이력을 시계열로 저장하는 `USAGE_LOG` 테이블을 분리하여 추후 통계 분석이 가능한 구조를 취합니다.

## 🚀 Features (MVP)
- **진동 데이터 수집:** 세탁기에 부착된 SW-420 센서가 작동 상태를 감지하여 MQTT 브로커로 발행(Publish).
- **상태 판별 알고리즘:** Spring Boot 서버에서 데이터를 구독(Subscribe)하고, 일정 시간 이상 진동이 감지되지 않을 경우 '세탁 완료' 상태로 전환.
- **실시간 알림:** 세탁 완료 판별 즉시 Discord Webhook을 통해 지정된 채널로 완료 메세지 전송.

## 📈 Roadmap
- [x] Phase 1: 백엔드 초기 뼈대 구축 및 디스코드 웹훅 연동 테스트
- [ ] Phase 2: NodeMCU & SW-420 하드웨어 회로 구성 및 MQTT 통신 구현
- [ ] Phase 3: JPA 엔티티 구성 (Machine, UsageLog 등) 및 비즈니스 로직 완성
- [ ] Phase 4: (Optional) 사용자 대시보드용 웹 프론트엔드 구축
