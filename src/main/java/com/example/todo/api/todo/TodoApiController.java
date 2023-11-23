package com.example.todo.api.todo;

import com.example.todo.dto.ResponseDto;
import com.example.todo.dto.todo.TodoApiDto;
import com.example.todo.exception.ErrorCode;
import com.example.todo.exception.TodoAppException;
import com.example.todo.service.todo.TodoApiService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/todo")
public class TodoApiController {
    private final TodoApiService service;

    @PostMapping
    public ResponseDto create(
            @RequestBody TodoApiDto todoApiDto,
            @Nullable @RequestParam(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication) throws IOException {
        Long userId = Long.parseLong(authentication.getName());
        return service.createTodo(userId,todoApiDto, files);
    }

    //Todo 상세 조회
    @GetMapping("/{todoId}")
    public TodoApiDto read(@PathVariable("todoId") Long todoId) {
        return service.readTodo(todoId);
    }

    //특정 유저 Todo 목록 조회
    @GetMapping("/users/{userId}")
    public Page<TodoApiDto> readAll(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "limit", defaultValue = "5") Integer limit) {
        service.findUserById(userId);
        Pageable pageable = PageRequest.of(page, limit);
        return service.readUserTodoAll(userId, pageable);
    }

    //Todo 수정
    @PutMapping("/{todoId}")
    public ResponseDto update(
            @PathVariable("todoId") Long todoId,
            @RequestBody TodoApiDto todoApiDto,
            @Nullable @RequestParam(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication) throws IOException {
        Long userId = Long.parseLong(authentication.getName());
        return service.updateTodo(userId, todoId, todoApiDto, files);
    }

    //Todo 삭제
    @DeleteMapping("/{todoId}")
    public ResponseDto delete(
            @PathVariable("todoId") Long todoId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return service.deleteTodo(userId, todoId);
    }
    //Todo 좋아요 추가, 취소 기능
    @PostMapping("/{todoId}/like")
    public ResponseDto likeTodo(Authentication authentication,
                                @PathVariable("todoId") Long todoId) {
        Long userId = Long.parseLong(authentication.getName());
        boolean like = service.likeTodo(userId, todoId);

        ResponseDto responseDto = new ResponseDto();
        if (like) responseDto.setMessage("해당 TODO를 좋아합니다.");
        else responseDto.setMessage("해당 TODO 좋아요를 취소합니다.");

        return responseDto;
    }
}

