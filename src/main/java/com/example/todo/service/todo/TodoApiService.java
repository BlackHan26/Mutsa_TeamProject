package com.example.todo.service.todo;

import com.example.todo.domain.entity.FileEntity;
import com.example.todo.domain.entity.LikeEntity;
import com.example.todo.domain.entity.user.User;
import com.example.todo.domain.repository.FileRepository;
import com.example.todo.domain.repository.LikeRepository;
import com.example.todo.domain.repository.TodoApiRepository;
import com.example.todo.domain.repository.user.UserRepository;
import com.example.todo.dto.ResponseDto;
import com.example.todo.dto.todo.TodoApiDto;
import com.example.todo.domain.entity.TodoApiEntity;
import com.example.todo.exception.ErrorCode;
import com.example.todo.exception.TodoAppException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TodoApiService {
    private final TodoApiRepository todoApiRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final FileRepository fileRepository;

    // 해당 To do가 존재하는지 확인하는 메소드
    public TodoApiEntity findTodoById(Long id) {
        return todoApiRepository.findById(id)
                .orElseThrow(() -> new TodoAppException(ErrorCode.NOT_FOUND_TODO));
    }

    // 해당 유저가 존재하는지 확인
    public User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new TodoAppException(ErrorCode.NOT_FOUND_USER));
    }
    @Transactional
    // To do 등록
    public ResponseDto createTodo(Long userId, TodoApiDto todoApiDto, List<MultipartFile> files) throws IOException {
        // TodoEntity 생성 및 저장
        TodoApiEntity todoApiEntity = createTodoEntity(userId, todoApiDto);
        // 파일 저장
        saveFiles(userId, todoApiEntity.getId(), files);
        return new ResponseDto("Todo 등록이 완료되었습니다.");
    }
    // TodoEntity를 생성하고 저장하는 메소드
    private TodoApiEntity createTodoEntity(Long userId, TodoApiDto todoApiDto) {
        TodoApiEntity todoApiEntity = new TodoApiEntity();
        // 유저 확인
        User user = findUserById(userId);

        todoApiEntity.setUser(user);
        todoApiEntity.setTitle(todoApiDto.getTitle());
        todoApiEntity.setContent(todoApiDto.getContent());
        todoApiEntity.setStartDate(todoApiDto.getStartDate());
        todoApiEntity.setDueDate(todoApiDto.getDueDate());

        // 현재 날짜를 기준으로 Status 설정
        LocalDate currentDate = LocalDate.now();
        todoApiEntity.setStatus(calculateTodoStatus(todoApiDto.getStartDate(), todoApiDto.getDueDate(), currentDate));

        return todoApiRepository.save(todoApiEntity);
    }
    // by 최강성 : 파일 첨부 기능 추가
    private void saveFiles(Long userId, Long todoId, List<MultipartFile> files) throws IOException {
        if (files != null) {
            int order = 0;
            String dirUrl = String.format("todo/media/user%d/todo%d", userId, todoId);
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    order++;
                    String[] fileName = file.getOriginalFilename().split("\\.");
                    String fileUrl = String.format(dirUrl + "/%d-%s.%s", order, fileName[0], fileName[1]);
                    file.transferTo(Path.of(fileUrl));
                    FileEntity fileEntity = new FileEntity();
                    fileEntity.setTodoId(todoId);
                    fileEntity.setUrl(fileUrl);
                    fileRepository.save(fileEntity);
                }
            }
        }
    }
    // 현재날짜를 기준으로 Status 설정하는 메서드
    private String calculateTodoStatus(LocalDate startDate, LocalDate dueDate, LocalDate currentDate) {
        if (startDate.isAfter(currentDate)) {
            return "진행예정";
        } else if (dueDate.isBefore(currentDate)) {
            return "완료";
        } else {
            return "진행중";
        }
    }

    // To do 상세 조회
    public TodoApiDto readTodo(Long todoId) {
        Optional<TodoApiEntity> optionalTodoApiEntity = todoApiRepository.findById(todoId);
        if (optionalTodoApiEntity.isPresent()) {
            TodoApiDto todoApiDto = TodoApiDto.fromEntity(optionalTodoApiEntity.get());
            List<FileEntity> fileEntityList = fileRepository.findAllByTodoId(todoId);
            for (FileEntity fileEntity : fileEntityList) {
                // Soft Delete된 파일은 제외
                if (fileEntity.getDeletedAt() != null) todoApiDto.getFileUrls().add(fileEntity.getUrl());
            }
            return todoApiDto;
        } else throw new TodoAppException(ErrorCode.NOT_FOUND_TODO);
    }

    // 특정 유저 To do 목록 조회
    public Page<TodoApiDto> readUserTodoAll(Long userId, Pageable pageable) {
        // 유저 확인
        findUserById(userId);
        // 페이징된 To do 목록 조회
        Page<TodoApiEntity> todoApiEntities = todoApiRepository.findByUserId(userId, pageable);
        return todoApiEntities.map(TodoApiDto::fromEntity);
    }

    // To do 수정
    public ResponseDto updateTodo(Long userId, Long todoId, TodoApiDto todoApiDto, List<MultipartFile> files) throws IOException {
        // 유저와 To do 확인
        TodoApiEntity todoApiEntity = findTodoById(todoId);
        User user = findUserById(userId);

        // To do 작성자인지 확인
        if (!todoApiEntity.getUser().getId().equals(userId)) {
            throw new TodoAppException(ErrorCode.NOT_MATCH_USERID);
        }

        todoApiEntity.setUser(user);
        todoApiEntity.setTitle(todoApiDto.getTitle());
        todoApiEntity.setContent(todoApiDto.getContent());
        todoApiEntity.setStartDate(todoApiDto.getDueDate());
        todoApiEntity.setDueDate(todoApiDto.getDueDate());
        // 현재 날짜 추가
        LocalDate currentDate = LocalDate.now();

        todoApiEntity.setStatus(calculateTodoStatus(todoApiDto.getStartDate(), todoApiDto.getDueDate(), currentDate));

        todoApiRepository.save(todoApiEntity);

        // 파일 soft delete 및 업데이트
        updateFiles(userId, todoId, files);

        return new ResponseDto("Todo가 수정 되었습니다.");
    }

    // by 최강성 : 파일 업데이트 및 추가
    private void updateFiles(Long userId, Long todoId, List<MultipartFile> files) throws IOException {
        if (files != null) {
            // 해당 Todo에 연결된 모든 파일 조회
            List<FileEntity> fileEntityList = fileRepository.findAllByTodoId(todoId);
            fileEntityList.forEach(fileEntity -> {
                // Soft Delete 처리
                fileEntity.setDeletedAt(LocalDateTime.now());
                fileRepository.save(fileEntity);
            });
            // 파일 저장
            saveFiles(userId, todoId, files);
        }
    }

    // To do 삭제
    public ResponseDto deleteTodo(Long userId, Long todoId) {
        //To do 확인

        TodoApiEntity todoApiEntity = findTodoById(todoId);
        //To do 작성자인지 확인

        if (!todoApiEntity.getUser().getId().equals(userId)) {
            throw new TodoAppException(ErrorCode.NOT_MATCH_USERID);
        }
        try {
            updateFiles(userId, todoId, null);
        } catch (IOException e) {
            // IOException 처리 로직 추가
            e.printStackTrace();
        }
        //삭제
        todoApiRepository.deleteById(todoApiEntity.getId());
        return new ResponseDto("Todo가 삭제되었습니다.");
    }

    // 파일 업데이트 및 추가
    public boolean likeTodo(Long userId, Long todoId) {
        // 유저 확인
        User user = userRepository.findById(userId).orElseThrow(() -> new TodoAppException(ErrorCode.NOT_FOUND_USER));
        // To do 확인
        TodoApiEntity todoApiEntity = findTodoById(todoId);
        Optional<LikeEntity> optionalLikeEntity = likeRepository.findByUserIdAndTodoId(userId, todoId);
        boolean result;

        if (optionalLikeEntity.isPresent()) {
            // 좋아요 취소
            likeRepository.delete(optionalLikeEntity.get());
            todoApiEntity.setLikes(todoApiEntity.getLikes() - 1);
            result = false;
        } else {
            // 좋아요 등록
            LikeEntity likeEntity = new LikeEntity();
            likeEntity.setUserId(userId);
            likeEntity.setTodoId(todoId);
            likeRepository.save(likeEntity);
            todoApiEntity.setLikes(todoApiEntity.getLikes() + 1);
            result = true;
        }
        // To do 저장
        todoApiRepository.save(todoApiEntity);
        return result;
    }
}


