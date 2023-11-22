package com.example.todo.service.task;

import com.example.todo.domain.entity.MemberEntity;
import com.example.todo.domain.entity.TeamEntity;
import com.example.todo.domain.entity.TaskApiEntity;
import com.example.todo.domain.entity.user.User;
import com.example.todo.domain.repository.MemberRepository;
import com.example.todo.domain.repository.TaskApiRepository;
import com.example.todo.domain.repository.user.UserRepository;
import com.example.todo.dto.NotificationDto;
import com.example.todo.dto.ResponseDto;
import com.example.todo.dto.task.TaskApiDto;
import com.example.todo.dto.task.TaskCreateDto;
import com.example.todo.dto.team.TeamOverviewDto;
import com.example.todo.exception.ErrorCode;
import com.example.todo.exception.TodoAppException;
import com.example.todo.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.time.LocalDate;
import java.util.stream.Collectors;


import static com.example.todo.service.team.TeamService.FREE_TEAM_PARTICIPANT_NUM;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskApiService {
    private final TaskApiRepository taskApiRepository;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;
    private final TaskValidationUtils taskValidationUtils;

    //업무 등록하는 메서드
    public ResponseDto createTask(Long userId, Long teamId, TaskCreateDto taskCreateDto) {
        log.info("TaskApiService createTask1");
        //팀 존재 확인
        TeamEntity teamEntity = taskValidationUtils.getTeamById(teamId);
        //by안채연,기능을 사용할 수 있는지 확인
        taskValidationUtils.isAvailableFunction(teamEntity);
        // 사용자가 해당 팀의 멤버인지 확인
        taskValidationUtils.isMemberOfTeam(userId, teamId);
        // 담당자가 있는 유저인지 확인
        log.info("{}", taskCreateDto.getWorker());
        User worker = userRepository.findByUsername(taskCreateDto.getWorker())
                .orElseThrow(() -> new TodoAppException(ErrorCode.NOT_FOUND_USER));

        // 담당자가 팀의 멤버인지 확인
        taskValidationUtils.isMemberOfTeam(worker.getId(), teamId);
        //담당자를 존재를 확인하고 꺼내온다.
        MemberEntity workerMember = memberRepository.findByTeamAndUser(teamEntity, worker)
                .orElseThrow(() -> new TodoAppException(ErrorCode.NOT_FOUND_MEMBER)
                );
        TaskApiEntity taskApiEntity = createTaskEntity(userId, teamEntity, taskCreateDto, workerMember);
        taskApiEntity = taskApiRepository.save(taskApiEntity);

        return new ResponseDto("업무가 등록되었습니다.");
    }

    //새로운 업무 엔터티를 생성하여 반환하는 메서드
    private TaskApiEntity createTaskEntity(Long userId, TeamEntity teamEntity, TaskCreateDto taskCreateDto, MemberEntity workerMember) {
        TaskApiEntity taskApiEntity = new TaskApiEntity();
        taskApiEntity.setUserId(userId);
        taskApiEntity.setTeam(teamEntity);
        taskApiEntity.setTaskName(taskCreateDto.getTaskName());
        taskApiEntity.setTaskDesc(taskCreateDto.getTaskDesc());
        taskApiEntity.setStartDate(taskCreateDto.getStartDate());
        taskApiEntity.setDueDate(taskCreateDto.getDueDate());
        taskApiEntity.setMember(workerMember);

        //현재날짜에 맞춰서 업무 상태를 저장
        LocalDate currentDate = LocalDate.now();
        taskApiEntity.setStatus("진행중");
        //설정 날짜가 미래라면 진행 예정
        if (taskCreateDto.getStartDate().isAfter(currentDate)) {
            taskApiEntity.setStatus("진행예정");
            //설정 날짜가 과거라면 완료
        } else if (taskCreateDto.getDueDate().isBefore(currentDate)) {
            taskApiEntity.setStatus("완료");
        }
        return taskApiEntity;
    }

    //업무 상세 조회하는 메서드
    public TaskApiDto readTask(Long teamId, Long taskId, Long userId) {
        // 사용자가 해당 팀의 멤버인지 확인
        taskValidationUtils.isMemberOfTeam(userId, teamId);
        //조직이 존재하는지 확인
        taskValidationUtils.getTeamById(teamId);
        //업무가 존재하는지 확인
        return taskApiRepository.findById(taskId)
                .filter(taskApiEntity -> taskApiEntity.getTeam().getId().equals(teamId))
                .map(TaskApiDto::fromEntity)
                .orElseThrow(() -> new TodoAppException(ErrorCode.NOT_FOUND_TASK));
    }

    //업무 전부 조회하는 메서드
    public List<TaskApiDto> readTasksAll(Long userId, Long teamId) {
        // 사용자가 해당 팀의 멤버인지 확인
        taskValidationUtils.isMemberOfTeam(userId, teamId);
        //조직이 존재하는지 확인
        taskValidationUtils.getTeamById(teamId);

        List<TaskApiEntity> taskApiEntities = taskApiRepository.findAllByTeamId(teamId);
        return taskApiEntities.stream()
                .map(TaskApiDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 내가 속한 팀들의 업무를 전부 조회하는 메서드
    public Map<TeamOverviewDto, List<TaskApiDto>> getMyTasks(Long userId) {
        Map<TeamOverviewDto, List<TaskApiDto>> myTasks = new HashMap<>();
        //by 최강성:TeamOverviewDto 추가
        List<MemberEntity> memberEntities = memberRepository.findAllByUserId(userId);
        for (MemberEntity memberEntity : memberEntities) {
            TeamEntity teamEntity = memberEntity.getTeam();
            List<TaskApiEntity> taskApiEntityList = taskApiRepository.findAllByTeamIdAndMember_UserId(teamEntity.getId(), userId);
            List<TaskApiDto> taskApiDtoList = new ArrayList<>();
            for (TaskApiEntity taskApiEntity : taskApiEntityList)
                //by 최강성 : 완료 된 업무는 제외
                if (!taskApiEntity.getStatus().equals("완료")) {
                    taskApiDtoList.add(TaskApiDto.fromEntity(taskApiEntity));
                }
            myTasks.put(TeamOverviewDto.fromEntity(teamEntity), taskApiDtoList);
        }
        return myTasks;
    }

    //업무 수정 메서드
    public ResponseDto updateTask(Long userId, Long teamId, Long taskId, TaskApiDto taskApiDto) {
        //팀 존재 확인
        TeamEntity teamEntity = taskValidationUtils.getTeamById(teamId);
        //기능을 사용할 수 있는지 확인
        taskValidationUtils.isAvailableFunction(teamEntity);
        TaskApiEntity taskApiEntity = taskValidationUtils.getTaskById(taskId);
        //업무관리자와 대상업무가 맞는지 확인
        taskValidationUtils.validateTaskAndUser(userId, teamId, taskApiEntity);

        // 이전 상태 저장
        String previousStatus = taskApiEntity.getStatus();

        // 새로운 정보로 업무 업데이트
        updateTaskDetails(taskApiEntity, userId, taskApiDto);

        //업무 수정후 업무 상태가 이전 상태와 달라졌을때만 알림보내기
        if (!previousStatus.equals(taskApiEntity.getStatus())) {
            sendTaskStatusNotification(taskApiEntity);
        }

        // 업무 저장
        taskApiRepository.save(taskApiEntity);

        return new ResponseDto("업무가 수정되었습니다.");
    }
    //업무 업데이트 메서드
    private void updateTaskDetails(TaskApiEntity taskApiEntity, Long userId, TaskApiDto taskApiDto) {
        taskApiEntity.setUserId(userId);
        taskApiEntity.setTaskName(taskApiDto.getTaskName());
        taskApiEntity.setTaskDesc(taskApiDto.getTaskDesc());
        taskApiEntity.setStartDate(taskApiDto.getStartDate());
        taskApiEntity.setDueDate(taskApiDto.getDueDate());

        // 현재 날짜 추가
        LocalDate currentDate = LocalDate.now();
        taskApiEntity.setStatus("진행중");

        // 현재 날짜가 아직 startDate 이전이면 진행예정
        if (taskApiDto.getStartDate().isAfter(currentDate)) {
            taskApiEntity.setStatus("진행예정");
        } else if (taskApiDto.getDueDate().isBefore(currentDate)) {
            // 현재 날짜가 dueDate를 지났으면 완료
            taskApiEntity.setStatus("완료");
        }
    }

    //업무 삭제
    public ResponseDto deleteTask(Long userId, Long teamId, Long taskId) {
        TeamEntity teamEntity = taskValidationUtils.getTeamById(teamId);

        // 기능을 사용할 수 있는지 확인
        taskValidationUtils.isAvailableFunction(teamEntity);
        // 업무 존재 확인
        TaskApiEntity taskApiEntity = taskValidationUtils.getTaskById(taskId);
        //업무관리자와 대상업무가 맞는지 확인
        taskValidationUtils.validateTaskAndUser(userId, teamId, taskApiEntity);

        // 맞다면 진행
        taskApiRepository.deleteById(taskApiEntity.getId());
        return new ResponseDto("업무를 삭제했습니다.");
    }


    // by 최강성: 팀내 내 업무 조회하는 메서드
    public List<TaskApiDto> getMyTasksInATeam(Long userId, Long teamId) {
        List<TaskApiDto> myTasksInATeam = new ArrayList<>();
        //팀이 존재하는지 확인
        taskValidationUtils.getTeamById(teamId);
        // 사용자가 해당 팀의 멤버인지 확인
        taskValidationUtils.isMemberOfTeam(userId, teamId);

        List<TaskApiEntity> taskApiEntityList = taskApiRepository.findAllByTeamIdAndMember_UserId(teamId, userId);
        for (TaskApiEntity taskApiEntity : taskApiEntityList)
            if (!taskApiEntity.getStatus().equals("완료")) {
                myTasksInATeam.add(TaskApiDto.fromEntity(taskApiEntity));
            }
        return myTasksInATeam;

    }
    //매일 정각에 업무를 자동 업데이트 하는 메서드
    @Scheduled(cron = "0 0 0 * * *")
    public void updateTaskStatusAuto() {
        LocalDate currentDate = LocalDate.now();
        List<TaskApiEntity> tasks = taskApiRepository.findAll();

        for (TaskApiEntity task : tasks) {
            updateTaskStatus(task, currentDate);
        }
    }
    //업무 status를 수정하는 메서드
    private void updateTaskStatus(TaskApiEntity task, LocalDate currentDate) {
        if ("진행중".equals(task.getStatus())) {
            if (task.getDueDate().isBefore(currentDate)) {
                task.setStatus("완료");
                taskApiRepository.save(task);

                sendTaskStatusNotification(task);
            }
        } else if ("진행예정".equals(task.getStatus())) {
            if (task.getStartDate().isBefore(currentDate)) {
                task.setStatus("진행중");
                taskApiRepository.save(task);

                sendTaskStatusNotification(task);
            }
        }
    }
    //업무 수정 시 전체 알림을 보내는 메서드
    private void sendTaskStatusNotification(TaskApiEntity taskApiEntity) {
        String formattedTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        NotificationDto notificationDto = new NotificationDto();
        notificationDto.setContent(String.format("'%s'팀의 업무'%s'의 진행상황이 '%s'(으)로 변경되었습니다. %s",
                taskApiEntity.getTeam().getName(), taskApiEntity.getTaskName(),
                taskApiEntity.getStatus(), formattedTime));
        notifyTeamMembers(taskApiEntity.getTeam().getId(), notificationDto);
    }
    // 팀에 속한 멤버들에게 알림을 보내는 메서드
    private void notifyTeamMembers(Long teamId, NotificationDto notificationDto) {
        // 팀에 속한 모든 멤버를 검색
        List<MemberEntity> teamMembers = memberRepository.findAllByTeamId(teamId);

        // 각 멤버에게 알림을 보냄
        for (MemberEntity member : teamMembers) {
            Long userId = member.getUser().getId();
            notificationService.notify(userId, notificationDto);
        }
    }
}