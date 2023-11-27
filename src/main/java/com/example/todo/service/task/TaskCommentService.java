package com.example.todo.service.task;

import com.example.todo.domain.entity.*;
import com.example.todo.domain.entity.user.User;
import com.example.todo.domain.repository.*;
import com.example.todo.domain.repository.user.UserRepository;
import com.example.todo.dto.task.*;
import com.example.todo.exception.ErrorCode;
import com.example.todo.exception.TodoAppException;
import com.example.todo.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TaskCommentService {
    private final TaskCommentRepository taskCommentRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final TeamReposiotry teamReposiotry;
    private final TaskApiRepository taskApiRepository;
    private final NotificationService notificationService;
    private final TaskValidationUtils taskValidationUtils;
    private final TaskCommentReplyRepository taskCommentReplyRepository;

    public void createTaskComment(Long userId, Long teamId, Long taskId, TaskCommentCreateDto taskCommentCreateDto) {
        // 사용자 존재 여부 확인
        User user = taskValidationUtils.getUserById(userId);
        // 팀 존재 여부 확인
        TeamEntity team = taskValidationUtils.getTeamById(teamId);
        // 업무 존재 여부 확인
        TaskApiEntity taskApiEntity = taskValidationUtils.getTaskById(taskId);
        // 사용자가 팀의 멤버인지 확인
        taskValidationUtils.isMemberOfTeam(userId, teamId);

        TaskCommentEntity taskCommentEntity = new TaskCommentEntity();
        taskCommentEntity.setWriter(user);
        taskCommentEntity.setContent(taskCommentCreateDto.getContent());
        taskCommentEntity.setTaskApiEntity(taskApiEntity);
        taskCommentRepository.save(taskCommentEntity);

        // 댓글을 작성한 사용자와 업무 관리자를 비교
        if (!user.getId().equals(taskApiEntity.getWorkerId())) {
            LocalDateTime currentTime = LocalDateTime.now(); // 현재 시간
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            String formattedTime = currentTime.format(formatter);

            // 알림 메시지 생성
            String message = "'" + team.getName() + "'팀의 " + user.getUsername() + "님이'" + taskApiEntity.getTaskName() + "'에 메시지를 남겼습니다. createdTime:" + formattedTime;            // 관리자에게 알림을 보냄
            notificationService.notify(taskApiEntity.getWorkerId(), message);
        } else throw new TodoAppException(ErrorCode.NOT_ALLOWED_MESSAGE);
    }

    public Page<TaskCommentReadDto> readTaskCommentsPage(Long userId, Long teamId, Long taskId, Integer page, Integer limit) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 유저 존재 X");
        User user = optionalUser.get();

        Optional<TeamEntity> optionalTeamEntity = teamReposiotry.findById(teamId);
        if (optionalTeamEntity.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 팀 존재 X");
        TeamEntity team = optionalTeamEntity.get();

        Optional<TaskApiEntity> optionalTaskApiEntity = taskApiRepository.findById(taskId);
        if (optionalTaskApiEntity.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 task 존재 X");
        TaskApiEntity taskApiEntity = optionalTaskApiEntity.get();

        Pageable pageable = PageRequest.of(page, limit, Sort.by("id").descending());
        Page<TaskCommentEntity> taskCommentEntityPage = taskCommentRepository.findAllByTaskApiEntity(taskApiEntity, pageable);
        Page<TaskCommentReadDto> commentDtoPage = taskCommentEntityPage.map(TaskCommentReadDto::fromEntity);
        return commentDtoPage;
    }

    public void updateTaskComment(Long userId, Long teamId, Long taskId, Long commentId, TaskCommentUpdateDto taskCommentUpdateDto) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 유저 존재 X");
        User user = optionalUser.get();

        Optional<TeamEntity> optionalTeamEntity = teamReposiotry.findById(teamId);
        if (optionalTeamEntity.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 팀 존재 X");
        TeamEntity team = optionalTeamEntity.get();

        Optional<TaskApiEntity> optionalTaskApiEntity = taskApiRepository.findById(taskId);
        if (optionalTaskApiEntity.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 task 존재 X");
        TaskApiEntity taskApiEntity = optionalTaskApiEntity.get();

        Optional<MemberEntity> optionalMemberEntity = memberRepository.findByTeamAndUser(team, user);
        if (optionalMemberEntity.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 팀에 가입X");

        Optional<TaskCommentEntity> optionalTaskCommentEntity = taskCommentRepository.findById(commentId);
        if (optionalTaskCommentEntity.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 TaskComment가 존재X");
        TaskCommentEntity taskCommentEntity = optionalTaskCommentEntity.get();

        taskCommentEntity.setContent(taskCommentUpdateDto.getContent());
        taskCommentRepository.save(taskCommentEntity);
    }

    //답글 달기
    public TaskCommentReplyEntity addReply(Long userId, Long teamId, Long taskId, Long commentId, TaskCommentReplyDto taskCommentReplyDto) {
        // 사용자, 팀, 업무, 댓글 존재 여부 확인
        User user = taskValidationUtils.getUserById(userId);
        TeamEntity team = taskValidationUtils.getTeamById(teamId);
        TaskApiEntity taskApiEntity = taskValidationUtils.getTaskById(taskId);
        TaskCommentEntity taskCommentEntity = taskValidationUtils.getTaskCommentById(commentId);
        taskValidationUtils.isMemberOfTeam(userId, teamId);

        //맞다면 진행
        TaskCommentReplyEntity replyEntity = createReplyEntity(taskCommentEntity, user, taskCommentReplyDto);

        // 알림을 받을 사용자의 ID를 가져오기 위해 TaskCommentEntity를 사용하여 작성자의 ID를 가져옴
        Long receiveUserId = taskCommentEntity.getWriter().getId();
        boolean isWorker = userId.equals(taskApiEntity.getWorkerId());
        //알림 보내기
        sendNotifications(userId, taskApiEntity, team, user, formatCurrentTime(), receiveUserId, isWorker);

        return taskCommentReplyRepository.save(replyEntity);
    }
    private TaskCommentReplyEntity createReplyEntity(TaskCommentEntity taskCommentEntity, User user, TaskCommentReplyDto taskCommentReplyDto) {
        TaskCommentReplyEntity replyEntity = new TaskCommentReplyEntity();
        replyEntity.setTaskCommentEntity(taskCommentEntity);
        replyEntity.setWriter(user);
        replyEntity.setReply(taskCommentReplyDto.getReply());
        return replyEntity;
    }
    private void sendNotifications(Long userId, TaskApiEntity taskApiEntity, TeamEntity team, User user, String formattedTime, Long receiveUserId, boolean isWorker) {
        String message = NotificationMessage(team, user, taskApiEntity, formattedTime);
        // 답글을 작성한 사용자와 댓글 작성자가 다를 때 알림을 보냄
        if (!userId.equals(receiveUserId)) {
            // 댓글 작성자에게 알림 보내기
            notificationService.notify(receiveUserId, message);
            if (!isWorker) {//업무담당자도 아닌, 제3자라면
                // 업무 담당자에게도 알림 보내기
                notificationService.notify(taskApiEntity.getWorkerId(), message);
            }
        } else {// 댓쓴이가 답글을 달았다면, 담당자에게 알림 보내기
            notificationService.notify(taskApiEntity.getWorkerId(), message);
        }
    }
    private String NotificationMessage(TeamEntity team, User user, TaskApiEntity taskApiEntity, String formattedTime) {
        return "'" + team.getName() + "'팀의 " + user.getUsername() + "님이 '" + taskApiEntity.getTaskName() + "'에 메시지를 남겼습니다. createdTime:" + formattedTime;
    }
    private String formatCurrentTime() {
        LocalDateTime currentTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return currentTime.format(formatter);
    }
}
