package com.projecthub.service;

import com.projecthub.dto.Dtos.*;
import com.projecthub.model.*;
import com.projecthub.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional
public class ProjectService {

    private final ProjectRepository projects;
    private final UserRepository users;
    private final TaskRepository tasks;
    private final FeedbackRepository feedbacks;

    public ProjectService(ProjectRepository projects, UserRepository users,
                          TaskRepository tasks, FeedbackRepository feedbacks) {
        this.projects = projects;
        this.users = users;
        this.tasks = tasks;
        this.feedbacks = feedbacks;
    }

    // ------------------------------------------------------------ helpers

    public User currentUser() {
        String email = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private Project load(Long id) {
        return projects.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private boolean isTeamMember(Project p, User u) {
        return p.getOwner().getId().equals(u.getId())
                || p.getMembers().stream().anyMatch(m -> m.getId().equals(u.getId()));
    }

    /** Faculty can see everything, students only the projects they belong to. */
    private Project loadAccessible(Long id, User u) {
        Project p = load(id);
        if (u.getRole() != Role.FACULTY && !isTeamMember(p, u)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not part of this project");
        }
        return p;
    }

    private Project loadForTeam(Long id, User u) {
        Project p = load(id);
        if (!isTeamMember(p, u)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only team members can do this");
        }
        return p;
    }

    public static UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole());
    }

    private ProjectDto toDto(Project p) {
        long total = tasks.countByProjectId(p.getId());
        long done = tasks.countByProjectIdAndStatus(p.getId(), TaskStatus.DONE);
        int progress = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
        List<UserDto> members = p.getMembers().stream()
                .sorted(Comparator.comparing(User::getName))
                .map(ProjectService::toDto).toList();
        return new ProjectDto(p.getId(), p.getTitle(), p.getDescription(), p.getTechnologies(),
                p.getStatus(), toDto(p.getOwner()), members, progress, total, p.getCreatedAt());
    }

    private TaskDto toDto(Task t) {
        User a = t.getAssignee();
        return new TaskDto(t.getId(), t.getTitle(), t.getDescription(), t.getStatus(),
                a == null ? null : a.getId(), a == null ? null : a.getName(), t.getDueDate());
    }

    private FeedbackDto toDto(Feedback f) {
        return new FeedbackDto(f.getId(), f.getComment(), toDto(f.getAuthor()), f.getCreatedAt());
    }

    // ------------------------------------------------------------ projects

    @Transactional(readOnly = true)
    public List<ProjectDto> list() {
        User u = currentUser();
        List<Project> found = u.getRole() == Role.FACULTY
                ? projects.findAllByOrderByCreatedAtDesc()
                : projects.findForUser(u);
        return found.stream().map(this::toDto).toList();
    }

    public ProjectDto create(ProjectRequest req) {
        User u = currentUser();
        if (u.getRole() != Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only students can create projects");
        }
        Project p = new Project();
        p.setTitle(req.title().trim());
        p.setDescription(req.description());
        p.setTechnologies(req.technologies());
        p.setOwner(u);
        p.getMembers().add(u);
        return toDto(projects.save(p));
    }

    @Transactional(readOnly = true)
    public ProjectDto get(Long id) {
        return toDto(loadAccessible(id, currentUser()));
    }

    public ProjectDto update(Long id, ProjectRequest req) {
        Project p = loadForTeam(id, currentUser());
        if (p.getStatus() == ProjectStatus.SUBMITTED || p.getStatus() == ProjectStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This project can no longer be edited");
        }
        p.setTitle(req.title().trim());
        p.setDescription(req.description());
        p.setTechnologies(req.technologies());
        return toDto(p);
    }

    public ProjectDto addMember(Long id, MemberRequest req) {
        User me = currentUser();
        Project p = loadForTeam(id, me);
        User member = users.findByEmail(req.email().trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No user with that email"));
        if (member.getRole() != Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only students can join a team");
        }
        p.getMembers().add(member);
        return toDto(p);
    }

    public ProjectDto submit(Long id) {
        Project p = loadForTeam(id, currentUser());
        if (p.getStatus() == ProjectStatus.SUBMITTED || p.getStatus() == ProjectStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is already " + p.getStatus());
        }
        p.setStatus(ProjectStatus.SUBMITTED);
        return toDto(p);
    }

    /** Faculty approves or rejects a submitted project. A rejected project can be edited and re-submitted. */
    public ProjectDto review(Long id, ReviewRequest req) {
        User u = currentUser();
        if (u.getRole() != Role.FACULTY) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only faculty can review projects");
        }
        if (req.decision() != ProjectStatus.APPROVED && req.decision() != ProjectStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED");
        }
        Project p = load(id);
        if (p.getStatus() != ProjectStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only submitted projects can be reviewed");
        }
        p.setStatus(req.decision());
        if (req.comment() != null && !req.comment().isBlank()) {
            saveFeedback(p, u, req.comment().trim());
        }
        return toDto(p);
    }

    // ------------------------------------------------------------ tasks

    @Transactional(readOnly = true)
    public List<TaskDto> listTasks(Long projectId) {
        loadAccessible(projectId, currentUser());
        return tasks.findByProjectIdOrderByIdAsc(projectId).stream().map(this::toDto).toList();
    }

    public TaskDto createTask(Long projectId, TaskRequest req) {
        Project p = loadForTeam(projectId, currentUser());
        Task t = new Task();
        t.setProject(p);
        apply(t, p, req);
        if (p.getStatus() == ProjectStatus.PLANNING) {
            p.setStatus(ProjectStatus.IN_PROGRESS);
        }
        return toDto(tasks.save(t));
    }

    public TaskDto updateTask(Long taskId, TaskRequest req) {
        Task t = tasks.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        loadForTeam(t.getProject().getId(), currentUser());
        apply(t, t.getProject(), req);
        return toDto(t);
    }

    public void deleteTask(Long taskId) {
        Task t = tasks.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        loadForTeam(t.getProject().getId(), currentUser());
        tasks.delete(t);
    }

    private void apply(Task t, Project p, TaskRequest req) {
        t.setTitle(req.title().trim());
        t.setDescription(req.description());
        t.setDueDate(req.dueDate());
        t.setStatus(req.status() == null ? TaskStatus.TODO : req.status());
        if (req.assigneeId() == null) {
            t.setAssignee(null);
        } else {
            User assignee = users.findById(req.assigneeId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignee not found"));
            if (!isTeamMember(p, assignee)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee must be a team member");
            }
            t.setAssignee(assignee);
        }
    }

    // ------------------------------------------------------------ feedback

    @Transactional(readOnly = true)
    public List<FeedbackDto> listFeedback(Long projectId) {
        loadAccessible(projectId, currentUser());
        return feedbacks.findByProjectIdOrderByCreatedAtAsc(projectId).stream().map(this::toDto).toList();
    }

    public FeedbackDto addFeedback(Long projectId, FeedbackRequest req) {
        User u = currentUser();
        Project p = loadAccessible(projectId, u);
        return toDto(saveFeedback(p, u, req.comment().trim()));
    }

    private Feedback saveFeedback(Project p, User author, String comment) {
        Feedback f = new Feedback();
        f.setProject(p);
        f.setAuthor(author);
        f.setComment(comment);
        return feedbacks.save(f);
    }
}
