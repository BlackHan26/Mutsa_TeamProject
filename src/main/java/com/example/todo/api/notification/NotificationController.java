package com.example.todo.api.notification;

import com.example.todo.dto.NotificationDto;
import com.example.todo.service.notification.NotificationService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    // SSE를 저장할 리스트를 생성
    private List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    //다른 도메인으로부터 Http요청 허용
    @CrossOrigin
    //메시지를 읽을 수 있는 출력창
    @RequestMapping(value = "/subscribe", consumes = MediaType.ALL_VALUE)
    public SseEmitter subscribe(final HttpServletResponse response) {

        //한글
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");

        SseEmitter sseEmitter = new SseEmitter(60L * 1000 * 60);
        emitters.add(sseEmitter);

        // SseEmitter가 완료될 때 emitters 리스트에서 제거
        //SseEmitter는 한번 응답을 보내고 나면 완료된 상태가 되기에
        //다시 사용하기 전에 응답을 보낸 SseEmitter 객체를 제거
        sseEmitter.onCompletion(() -> {
            emitters.remove(sseEmitter);
        });
        updateNews(new NotificationDto());
        return sseEmitter;
    }
    //아래는 Service에서 호출한다.
    @PostMapping(value = "/updateNews")
    public void updateNews(@RequestBody NotificationDto notificationDto) {
        for (SseEmitter emitter : emitters) {
            try {
                SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event().data(notificationDto);
                emitter.send(eventBuilder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    //개별 구독 페이지
    @GetMapping(value = "/subscribe/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable Long userId, final HttpServletResponse response) {
        return notificationService.subscribe(userId, response);
    }
    // 클라이언트가 데이터를 수신하기 위해 사용할 엔드포인트
    @PostMapping("/sendData/{userId}")
    public void sendData(@PathVariable Long userId) {
        notificationService.notify(userId, "data");
    }

    @GetMapping("/api/notifications/subscribe/{taskId}")
    public String showNotification(@PathVariable Long taskId, Model model) {
        // 업무 수정 메시지 생성 또는 데이터베이스에서 가져오기
        String message = "업무가 수정되었습니다";

        // 모델에 메시지 추가
        model.addAttribute("message", message);

        // 알림창 템플릿으로 이동
        return "notification";
    }
}

