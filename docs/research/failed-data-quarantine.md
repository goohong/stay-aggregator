---
topic: 변환하지 못한 데이터를 남기는 흔한 방식
checked: 2026-09-17
---

# 변환하지 못한 데이터를 남기는 방식 조사

요구사항의 선택 항목 "정규화 실패 데이터 격리"를 어떻게 할지 정하려고 조사했다.
**같은 잘못된 값이 매 검색마다 오면 행이 무한히 쌓이는 것**이 걱정이었고, 업계가 그것을 어떻게 다루는지 봤다.
여기에는 확인한 사실만 적고 판단은 해당 ADR 에 적는다.

## 요약

- 성격이 다른 두 가지가 있다. **다시 처리할 것을 모아 두는 것**(Dead Letter Channel)과 **분석하려고 관찰을 모으는 것**(오류 추적)이다
- 우리 요구는 "추후 분석"이라 뒤쪽에 가깝다
- 무한히 쌓이는 문제는 셋으로 다룬다. **같은 것을 그룹화해 세기**, **보관 기간**, **쌓이는 것 자체에 경보**

## 1. 다시 처리할 것을 모아 두는 쪽 — Dead Letter Channel

| 사실 | 원문 |
|---|---|
| 배달하지 못한 메시지를 따로 둔 채널로 옮긴다 | [Enterprise Integration Patterns, Dead Letter Channel](https://www.enterpriseintegrationpatterns.com/patterns/messaging/DeadLetterChannel.html) (Hohpe·Woolf): 문제 — "What will the messaging system do with a message it cannot deliver?" / 해법 — "When a messaging system determines that it cannot or should not deliver a message, it may elect to move the message to a Dead Letter Channel." |
| 목적은 디버깅이다. 처리되지 않은 것을 떼어 놓고 왜 실패했는지 본다 | [AWS SQS, Using dead-letter queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html): "DLQs are useful for debugging your application because you can isolate unconsumed messages to determine why processing did not succeed." |
| **보관 기간을 둔다.** 그리고 원래 큐보다 길게 잡으라고 한다 | 같은 곳, 절 "Understanding message retention periods for dead-letter queues": "it is a best practice to always set the retention period of a dead-letter queue to be longer than the retention period of the original queue." |
| 쌓이는 것에 경보를 걸 수 있다 | 같은 곳이 "how to configure an alarm for any messages moved to a dead-letter queue" 문서를 따로 안내한다 |

**우리와 다른 점** — 여기 모이는 것은 **다시 처리할 메시지**다. 우리가 뺀 항목은 다시 처리하지 않는다. 공급사가 고쳐야 끝난다.

## 2. 분석하려고 관찰을 모으는 쪽 — 오류 추적

| 사실 | 원문 |
|---|---|
| 일어난 일마다 **지문**을 매기고, **지문이 같으면 하나로 그룹화한다** | [Sentry, Event grouping](https://docs.sentry.io/concepts/data-management/event-grouping/): "A fingerprint is a way to uniquely identify an event, and all events have one." / "Events with the same fingerprint are grouped together into an issue." |
| 지문은 스택 트레이스·예외·메시지 같은 것으로 만든다 | 같은 곳 |

**이것이 "계속 쌓이지 않느냐"에 대한 업계의 답이다.** 같은 문제가 백 번 와도 기록은 하나이고 거기에 횟수와 시각이 붙는다.

다만 **하나로 그룹화한 기록(issue)마다 몇 번 일어났고 언제 처음·마지막이었는지를 함께 보여준다**는 것은
제품 화면 설명에서 본 것이고 문서 원문으로 대조하지 못했다. **확인 못 함**으로 둔다.

## 3. 우리 경우에 바로 걸리는 것

지문을 만들려면 **사유가 종류여야 한다.** 지금 우리 사유는 문장이고 값이 섞여 있다.

```
"숙박일 2026-10-06 의 재고가 없다"
"숙박일 2026-10-07 의 재고가 없다"
```

같은 문제인데 문장이 달라 지문이 달라진다. 날짜가 바뀌는 만큼 기록이 늘어난다.
**사유를 종류로 나누는 일이 Q17 이고**, 격리를 제대로 하려면 그것부터 정해야 한다는 뜻이다.

## 확인하지 못한 것

- 오류 추적 도구가 그룹화한 기록에 횟수·첫/마지막 시각을 어떻게 저장하는지의 내부 구조
- 이 방식들이 우리 규모(검색 한 건에 항목 수백)에서 어느 정도 비용인지. 측정하지 않았다
