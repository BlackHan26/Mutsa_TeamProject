package com.example.todo.service.task;

import com.example.todo.domain.entity.*;
import com.example.todo.domain.entity.enums.SubscriptionStatus;
import com.example.todo.domain.entity.user.User;
import com.example.todo.domain.repository.*;
import com.example.todo.domain.repository.user.UserRepository;
import com.example.todo.exception.ErrorCode;
import com.example.todo.exception.TodoAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.example.todo.service.team.TeamService.FREE_TEAM_PARTICIPANT_NUM;
@Component
@RequiredArgsConstructor
public class TaskValidationUtils {
    private final TaskApiRepository taskApiRepository;
    private final TeamReposiotry teamRepository;
    private final UserRepository userRepository;
    private final UsersSubscriptionRepository usersSubscriptionRepository;
    private final TaskCommentRepository taskCommentRepository;
    //회원인지 확인
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new TodoAppException(ErrorCode.NOT_FOUND_USER));
    }

    //조직이 존재하는지 확인하는 메서드
    public TeamEntity getTeamById(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new TodoAppException(ErrorCode.NOT_FOUND_TEAM));
    }

    //업무 존재하는지 확인하는 메서드
    public TaskApiEntity getTaskById(Long taskId) {
        return taskApiRepository.findById(taskId)
                .orElseThrow(() -> new TodoAppException(ErrorCode.NOT_FOUND_TASK));
    }

    public TaskCommentEntity getTaskCommentById(Long commentId) {
        return taskCommentRepository.findById(commentId)
                .orElseThrow(() -> new TodoAppException(ErrorCode.NOT_FOUND_COMMENT));
    }

    //멤버인지 확인하는 메서드
    public void isMemberOfTeam(Long userId, Long teamId) {
        TeamEntity teamEntity = getTeamById(teamId);
        if (teamEntity.getMembers().stream().noneMatch(member -> member.getUser().getId().equals(userId))) {
            throw new TodoAppException(ErrorCode.NOT_MATCH_MEMBERID);
        }
    }
    //by 안채연 : 기능을 사용할 수 있는지 확인하는 메서드
    public void isAvailableFunction(TeamEntity teamEntity) {
        if (teamEntity.getParticipantNumMax() > FREE_TEAM_PARTICIPANT_NUM) {
            UsersSubscriptionEntity usersSubscription = usersSubscriptionRepository.findByUsersAndSubscriptionStatus(teamEntity.getManager(), SubscriptionStatus.ACTIVE)
                    .orElseThrow(() -> new TodoAppException(ErrorCode.NOT_AVAILABLE_FUNCTION));
            if (usersSubscription.getSubscription().getMaxMember() < teamEntity.getParticipantNumMax())
                throw new TodoAppException(ErrorCode.NOT_AVAILABLE_FUNCTION);
        }
    }
    //삭제와 수정을 하기위해 사용하는 메서드
    public void validateTaskAndUser(Long userId, Long teamId, TaskApiEntity taskApiEntity) {
        // 대상 업무가 대상 팀의 업무가 맞는지
        if (!teamId.equals(taskApiEntity.getTeam().getId())) {
            throw new TodoAppException(ErrorCode.NOT_MATCH_TEAM_AND_TASK);
        }

        // 팀 관리자 or 업무 담당자
        if (!taskApiEntity.getUserId().equals(userId) && !taskApiEntity.getWorkerId().equals(userId)) {
            throw new TodoAppException(ErrorCode.NOT_MATCH_USERID);
        }
    }
}
