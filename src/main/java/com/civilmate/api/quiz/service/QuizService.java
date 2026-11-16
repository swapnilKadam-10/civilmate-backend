package com.civilmate.api.quiz.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.civilmate.api.library.dto.LibraryDtos.PageView;
import com.civilmate.api.quiz.dto.QuizDtos.AdminOption;
import com.civilmate.api.quiz.dto.QuizDtos.AdminQuestion;
import com.civilmate.api.quiz.dto.QuizDtos.AnswerRequest;
import com.civilmate.api.quiz.dto.QuizDtos.AttemptHistory;
import com.civilmate.api.quiz.dto.QuizDtos.AttemptQuestion;
import com.civilmate.api.quiz.dto.QuizDtos.AttemptView;
import com.civilmate.api.quiz.dto.QuizDtos.OptionView;
import com.civilmate.api.quiz.dto.QuizDtos.OptionWriteRequest;
import com.civilmate.api.quiz.dto.QuizDtos.QuestionWriteRequest;
import com.civilmate.api.quiz.dto.QuizDtos.QuestionImportRequest;
import com.civilmate.api.quiz.dto.QuizDtos.ImportValidation;
import com.civilmate.api.quiz.dto.QuizDtos.OptionWriteRequest;
import com.civilmate.api.quiz.dto.QuizDtos.QuizSummary;
import com.civilmate.api.quiz.dto.QuizDtos.QuizWriteRequest;
import com.civilmate.api.quiz.dto.QuizDtos.StartRequest;
import com.civilmate.api.quiz.dto.QuizDtos.TopicResult;

@Service
public class QuizService {
    private static final TypeReference<List<Map<String, Object>>> OPTION_JSON = new TypeReference<>() { };
    private static final TypeReference<List<String>> ID_JSON = new TypeReference<>() { };
    private static final Set<String> EXAMS = Set.of("GATE_CE", "SSC_JE_CE", "GENERAL_CIVIL");
    private static final Set<String> DIFFICULTIES = Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public QuizService(JdbcTemplate jdbc, ObjectMapper json, Clock clock) { this.jdbc = jdbc; this.json = json; this.clock = clock; }

    @Transactional(readOnly = true)
    public PageView<QuizSummary> quizzes(String examType, String subject, String difficulty, int page, int size) {
        String where = " WHERE z.status='PUBLISHED' AND (CAST(? AS text) IS NULL OR z.exam_type=?) AND (CAST(? AS text) IS NULL OR lower(z.subject)=lower(?)) AND (CAST(? AS text) IS NULL OR z.difficulty=? OR z.difficulty IS NULL)";
        Object[] filters = { blank(examType), blank(examType), blank(subject), blank(subject), blank(difficulty), blank(difficulty) };
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT z.id FROM quizzes z LEFT JOIN quiz_questions q ON q.quiz_id=z.id AND q.status='PUBLISHED'" + where + " GROUP BY z.id HAVING COUNT(q.id)>0) available", Long.class, filters);
        List<Object> args = new ArrayList<>(java.util.Arrays.asList(filters)); args.add(size); args.add((long) page * size);
        List<QuizSummary> result = jdbc.query("SELECT z.id,z.title,z.exam_type,z.subject,z.topic,z.difficulty,z.duration_minutes,z.status,COUNT(q.id) question_count,COALESCE(SUM(q.marks),0) total_marks FROM quizzes z LEFT JOIN quiz_questions q ON q.quiz_id=z.id AND q.status='PUBLISHED'" + where + " GROUP BY z.id HAVING COUNT(q.id)>0 ORDER BY lower(z.title) LIMIT ? OFFSET ?",
                (rs, row) -> quizSummary(rs), args.toArray());
        long count = total == null ? 0 : total;
        return new PageView<>(result, page, size, count, (int) Math.ceil((double) count / size));
    }

    @Transactional(readOnly = true)
    public QuizSummary quiz(UUID id) {
        return jdbc.query("SELECT z.id,z.title,z.exam_type,z.subject,z.topic,z.difficulty,z.duration_minutes,z.status,COUNT(q.id) question_count,COALESCE(SUM(q.marks),0) total_marks FROM quizzes z LEFT JOIN quiz_questions q ON q.quiz_id=z.id AND q.status='PUBLISHED' WHERE z.id=? AND z.status='PUBLISHED' GROUP BY z.id HAVING COUNT(q.id)>0",
                (rs, row) -> quizSummary(rs), id).stream().findFirst().orElseThrow(() -> notFound("Quiz not found."));
    }

    @Transactional
    public AttemptView start(UUID userId, StartRequest request) {
        QuizConfig config = resolveConfig(request);
        int requestedCount = request.questionCount() == null ? config.defaultQuestionCount : request.questionCount();
        int duration = request.durationMinutes() == null ? config.durationMinutes : request.durationMinutes();
        if (requestedCount < 1 || requestedCount > 100) throw badRequest("Question count must be between 1 and 100.");
        if (duration < 1 || duration > 240) throw badRequest("Time limit must be between 1 and 240 minutes.");

        StringBuilder sql = new StringBuilder("SELECT q.id,q.question_text,q.question_type,q.topic,q.marks,q.negative_marks,q.explanation,q.source_reference FROM quiz_questions q JOIN quizzes z ON z.id=q.quiz_id WHERE q.status='PUBLISHED' AND z.status='PUBLISHED'");
        List<Object> args = new ArrayList<>();
        if (config.quizId != null) { sql.append(" AND q.quiz_id=?"); args.add(config.quizId); }
        sql.append(" AND z.exam_type=? AND lower(z.subject)=lower(?)"); args.add(config.examType); args.add(config.subject);
        if (config.topic != null) { sql.append(" AND lower(q.topic)=lower(?)"); args.add(config.topic); }
        if (config.difficulty != null) { sql.append(" AND q.difficulty=?"); args.add(config.difficulty); }
        sql.append(" ORDER BY random() LIMIT ?"); args.add(requestedCount);
        List<SelectedQuestion> selected = jdbc.query(sql.toString(), (rs, row) -> selectedQuestion(rs), args.toArray());
        if (selected.size() != requestedCount) throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough published questions match this configuration.");

        Instant now = clock.instant();
        UUID attemptId = UUID.randomUUID();
        String title = config.title == null ? config.examType.replace('_', ' ') + " · " + config.subject : config.title;
        BigDecimal totalMarks = selected.stream().map(SelectedQuestion::marks).reduce(BigDecimal.ZERO, BigDecimal::add);
        jdbc.update("INSERT INTO quiz_attempts (id,user_id,quiz_id,quiz_title_snapshot,exam_type,subject,total_questions,total_marks,started_at,expires_at,status) VALUES (?,?,?,?,?,?,?,?,?,?, 'IN_PROGRESS')",
                attemptId, userId, config.quizId, title, config.examType, config.subject, selected.size(), totalMarks, timestamp(now), timestamp(now.plusSeconds(duration * 60L)));
        int order = 0;
        for (SelectedQuestion question : selected) {
            List<OptionView> options = options(question.id, false);
            List<UUID> correct = correctOptionIds(question.id);
            jdbc.update("INSERT INTO quiz_attempt_questions (id,attempt_id,question_id,display_order,question_text_snapshot,question_type,topic_snapshot,marks,negative_marks,options_snapshot,correct_option_ids,explanation_snapshot,source_reference_snapshot) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?)",
                    UUID.randomUUID(), attemptId, question.id, order++, question.text, question.type, question.topic, question.marks, question.negativeMarks,
                    writeJson(options), writeJson(correct.stream().map(UUID::toString).toList()), question.explanation, question.sourceReference);
        }
        return attempt(userId, attemptId);
    }

    @Transactional
    public AttemptView attempt(UUID userId, UUID attemptId) {
        AttemptRecord record = attemptRecord(userId, attemptId, false);
        if (record.status.equals("IN_PROGRESS") && !record.expiresAt.isAfter(clock.instant())) submitInternal(record.id, record.expiresAt);
        return renderAttempt(userId, attemptId);
    }

    @Transactional
    public AttemptView answer(UUID userId, UUID attemptId, UUID attemptQuestionId, AnswerRequest request) {
        AttemptRecord record = attemptRecord(userId, attemptId, true);
        if (record.status.equals("SUBMITTED")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Submitted attempts cannot be changed.");
        if (!record.expiresAt.isAfter(clock.instant())) { submitInternal(record.id, record.expiresAt); return renderAttempt(userId, attemptId); }
        AttemptQuestionRow question = attemptQuestion(attemptId, attemptQuestionId);
        List<UUID> selected = request.selectedOptionIds() == null ? List.of() : request.selectedOptionIds();
        if (new HashSet<>(selected).size() != selected.size()) throw badRequest("Selected answers contain duplicates.");
        Set<UUID> validOptions = question.options.stream().map(OptionView::id).collect(java.util.stream.Collectors.toSet());
        if (!validOptions.containsAll(selected)) throw badRequest("An answer option does not belong to this question.");
        if (question.type.equals("SINGLE") && selected.size() > 1) throw badRequest("Select only one answer for this question.");
        jdbc.update("UPDATE quiz_attempt_questions SET selected_option_ids=?::jsonb, visited=TRUE, marked_for_review=? WHERE id=? AND attempt_id=?",
                writeJson(selected.stream().map(UUID::toString).toList()), Boolean.TRUE.equals(request.markedForReview()), attemptQuestionId, attemptId);
        return renderAttempt(userId, attemptId);
    }

    @Transactional
    public AttemptView submit(UUID userId, UUID attemptId) {
        AttemptRecord record = attemptRecord(userId, attemptId, true);
        if (record.status.equals("IN_PROGRESS")) submitInternal(attemptId, clock.instant().isAfter(record.expiresAt) ? record.expiresAt : clock.instant());
        return renderAttempt(userId, attemptId);
    }

    @Transactional(readOnly = true)
    public PageView<AttemptHistory> history(UUID userId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempts WHERE user_id=? AND status='SUBMITTED'", Long.class, userId);
        List<AttemptHistory> rows = jdbc.query("SELECT id,quiz_title_snapshot,exam_type,subject,status,total_questions,score,correct_count,incorrect_count,unanswered_count,accuracy,started_at,submitted_at FROM quiz_attempts WHERE user_id=? AND status='SUBMITTED' ORDER BY submitted_at DESC LIMIT ? OFFSET ?",
                (rs, row) -> new AttemptHistory(rs.getObject("id", UUID.class), rs.getString("quiz_title_snapshot"), rs.getString("exam_type"), rs.getString("subject"), rs.getString("status"), rs.getInt("total_questions"), rs.getBigDecimal("score"), rs.getInt("correct_count"), rs.getInt("incorrect_count"), rs.getInt("unanswered_count"), rs.getBigDecimal("accuracy"), instant(rs, "started_at"), instant(rs, "submitted_at")),
                userId, size, (long) page * size);
        long count = total == null ? 0 : total;
        return new PageView<>(rows, page, size, count, (int) Math.ceil((double) count / size));
    }

    @Transactional(readOnly = true)
    public PageView<QuizSummary> adminQuizzes(String status, int page, int size) {
        String filter = blank(status);
        String where = " WHERE (CAST(? AS text) IS NULL OR z.status=?)";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM quizzes z" + where, Long.class, filter, filter);
        List<QuizSummary> rows = jdbc.query("SELECT z.id,z.title,z.exam_type,z.subject,z.topic,z.difficulty,z.duration_minutes,z.status,COUNT(q.id) question_count,COALESCE(SUM(q.marks),0) total_marks FROM quizzes z LEFT JOIN quiz_questions q ON q.quiz_id=z.id" + where + " GROUP BY z.id ORDER BY z.created_at DESC LIMIT ? OFFSET ?",
                (rs, row) -> quizSummary(rs), filter, filter, size, (long) page * size);
        long count = total == null ? 0 : total;
        return new PageView<>(rows, page, size, count, (int) Math.ceil((double) count / size));
    }

    @Transactional
    public QuizSummary createQuiz(QuizWriteRequest request) {
        validateQuiz(request);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO quizzes (id,title,exam_type,subject,topic,difficulty,duration_minutes,status) VALUES (?,?,?,?,?,?,?,?)",
                id, request.title().trim(), normalized(request.examType()), request.subject().trim(), blank(request.topic()), normalized(request.difficulty()), request.durationMinutes(), status(request.status()));
        return adminQuiz(id);
    }

    @Transactional
    public QuizSummary updateQuiz(UUID id, QuizWriteRequest request) {
        validateQuiz(request);
        int updated = jdbc.update("UPDATE quizzes SET title=?,exam_type=?,subject=?,topic=?,difficulty=?,duration_minutes=?,status=?,updated_at=? WHERE id=?",
                request.title().trim(), normalized(request.examType()), request.subject().trim(), blank(request.topic()), normalized(request.difficulty()), request.durationMinutes(), status(request.status()), timestamp(clock.instant()), id);
        if (updated == 0) throw notFound("Quiz not found.");
        return adminQuiz(id);
    }

    @Transactional
    public void archiveQuiz(UUID id) {
        if (jdbc.update("UPDATE quizzes SET status='ARCHIVED',updated_at=? WHERE id=?", timestamp(clock.instant()), id) == 0) throw notFound("Quiz not found.");
    }

    @Transactional(readOnly = true)
    public PageView<AdminQuestion> adminQuestions(UUID quizId, int page, int size) {
        if (!existsQuiz(quizId)) throw notFound("Quiz not found.");
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM quiz_questions WHERE quiz_id=?", Long.class, quizId);
        List<UUID> ids = jdbc.query("SELECT id FROM quiz_questions WHERE quiz_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?", (rs, row) -> rs.getObject("id", UUID.class), quizId, size, (long) page * size);
        List<AdminQuestion> rows = ids.stream().map(this::adminQuestion).toList();
        long count = total == null ? 0 : total;
        return new PageView<>(rows, page, size, count, (int) Math.ceil((double) count / size));
    }

    @Transactional
    public AdminQuestion createQuestion(QuestionWriteRequest request) {
        validateQuestion(request);
        if (!existsQuiz(request.quizId())) throw notFound("Quiz not found.");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO quiz_questions (id,quiz_id,question_text,question_type,topic,difficulty,marks,negative_marks,explanation,source_reference,status) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id, request.quizId(), request.text().trim(), normalized(request.type()), request.topic().trim(), normalized(request.difficulty()), request.marks(), request.negativeMarks(), request.explanation().trim(), request.sourceReference().trim(), status(request.status()));
        saveOptions(id, request.options());
        return adminQuestion(id);
    }

    @Transactional(readOnly = true)
    public ImportValidation validateQuestionImport(UUID quizId, QuestionImportRequest request) {
        if (!existsQuiz(quizId)) throw notFound("Quiz not found.");
        for (QuestionWriteRequest item : request.questions()) {
            if (!quizId.equals(item.quizId())) throw badRequest("Every imported question must target the selected quiz.");
            validateQuestion(item);
        }
        return new ImportValidation(request.questions().size(), "All questions passed validation. No records were saved.");
    }

    @Transactional
    public List<AdminQuestion> importQuestions(UUID quizId, QuestionImportRequest request) {
        validateQuestionImport(quizId, request);
        return request.questions().stream().map(this::createQuestion).toList();
    }

    @Transactional
    public AdminQuestion updateQuestion(UUID id, QuestionWriteRequest request) {
        validateQuestion(request);
        if (!existsQuiz(request.quizId())) throw notFound("Quiz not found.");
        int updated = jdbc.update("UPDATE quiz_questions SET quiz_id=?,question_text=?,question_type=?,topic=?,difficulty=?,marks=?,negative_marks=?,explanation=?,source_reference=?,status=?,updated_at=? WHERE id=?",
                request.quizId(), request.text().trim(), normalized(request.type()), request.topic().trim(), normalized(request.difficulty()), request.marks(), request.negativeMarks(), request.explanation().trim(), request.sourceReference().trim(), status(request.status()), timestamp(clock.instant()), id);
        if (updated == 0) throw notFound("Question not found.");
        jdbc.update("DELETE FROM quiz_options WHERE question_id=?", id);
        saveOptions(id, request.options());
        return adminQuestion(id);
    }

    @Transactional
    public void archiveQuestion(UUID id) {
        if (jdbc.update("UPDATE quiz_questions SET status='ARCHIVED',updated_at=? WHERE id=?", timestamp(clock.instant()), id) == 0) throw notFound("Question not found.");
    }

    private QuizSummary adminQuiz(UUID id) {
        return jdbc.query("SELECT z.id,z.title,z.exam_type,z.subject,z.topic,z.difficulty,z.duration_minutes,z.status,COUNT(q.id) question_count,COALESCE(SUM(q.marks),0) total_marks FROM quizzes z LEFT JOIN quiz_questions q ON q.quiz_id=z.id WHERE z.id=? GROUP BY z.id",
                (rs, row) -> quizSummary(rs), id).stream().findFirst().orElseThrow(() -> notFound("Quiz not found."));
    }

    private AdminQuestion adminQuestion(UUID id) {
        AdminQuestion question = jdbc.query("SELECT id,quiz_id,question_text,question_type,topic,difficulty,marks,negative_marks,explanation,source_reference,status FROM quiz_questions WHERE id=?",
                (rs, row) -> new AdminQuestion(rs.getObject("id", UUID.class), rs.getObject("quiz_id", UUID.class), rs.getString("question_text"), rs.getString("question_type"), rs.getString("topic"), rs.getString("difficulty"), rs.getBigDecimal("marks"), rs.getBigDecimal("negative_marks"), rs.getString("explanation"), rs.getString("source_reference"), rs.getString("status"), List.of()), id)
                .stream().findFirst().orElseThrow(() -> notFound("Question not found."));
        return new AdminQuestion(question.id(), question.quizId(), question.text(), question.type(), question.topic(), question.difficulty(), question.marks(), question.negativeMarks(), question.explanation(), question.sourceReference(), question.status(), adminOptions(id));
    }

    private List<AdminOption> adminOptions(UUID questionId) {
        return jdbc.query("SELECT id,option_label,option_text,is_correct,display_order FROM quiz_options WHERE question_id=? ORDER BY display_order",
                (rs, row) -> new AdminOption(rs.getObject("id", UUID.class), rs.getString("option_label"), rs.getString("option_text"), rs.getBoolean("is_correct"), rs.getInt("display_order")), questionId);
    }

    private void saveOptions(UUID questionId, List<OptionWriteRequest> options) {
        int order = 0;
        for (OptionWriteRequest option : options) jdbc.update("INSERT INTO quiz_options (id,question_id,option_label,option_text,is_correct,display_order) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), questionId, option.label().trim().toUpperCase(Locale.ROOT), option.text().trim(), option.correct(), order++);
    }

    private void validateQuiz(QuizWriteRequest request) {
        String exam = normalized(request.examType());
        if (!EXAMS.contains(exam)) throw badRequest("Exam type must be GATE_CE, SSC_JE_CE, or GENERAL_CIVIL.");
        String difficulty = normalized(request.difficulty());
        if (difficulty != null && !DIFFICULTIES.contains(difficulty)) throw badRequest("Select a supported difficulty.");
        status(request.status());
        if (request.durationMinutes() < 1 || request.durationMinutes() > 240) throw badRequest("Duration must be between 1 and 240 minutes.");
    }

    private void validateQuestion(QuestionWriteRequest request) {
        if (!Set.of("SINGLE", "MULTIPLE").contains(normalized(request.type()))) throw badRequest("Question type must be SINGLE or MULTIPLE.");
        if (!DIFFICULTIES.contains(normalized(request.difficulty()))) throw badRequest("Select a supported difficulty.");
        String questionStatus = status(request.status());
        if (request.options().size() < 2 || request.options().size() > 8) throw badRequest("Provide between 2 and 8 answer options.");
        Set<String> labels = request.options().stream().map(option -> option.label().trim().toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        if (labels.size() != request.options().size()) throw badRequest("Answer option labels must be unique.");
        long correct = request.options().stream().filter(OptionWriteRequest::correct).count();
        if (normalized(request.type()).equals("SINGLE") && correct != 1) throw badRequest("Single-correct questions require exactly one correct option.");
        if (normalized(request.type()).equals("MULTIPLE") && correct < 2) throw badRequest("Multiple-correct questions require at least two correct options.");
        if (request.marks().signum() <= 0 || request.negativeMarks().signum() < 0) throw badRequest("Marks must be positive and negative marks cannot be below zero.");
        if (questionStatus.equals("PUBLISHED") && request.sourceReference().isBlank()) throw badRequest("A source is required before publishing a question.");
    }

    private String status(String value) { String normalized = normalized(value); if (normalized == null || !Set.of("DRAFT", "REVIEW", "PUBLISHED", "ARCHIVED").contains(normalized)) throw badRequest("Status must be DRAFT, REVIEW, PUBLISHED, or ARCHIVED."); return normalized; }
    private boolean existsQuiz(UUID id) { Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM quizzes WHERE id=?)", Boolean.class, id); return Boolean.TRUE.equals(exists); }

    private void submitInternal(UUID attemptId, Instant submittedAt) {
        List<AttemptQuestionRow> questions = jdbc.query("SELECT id,question_type,topic_snapshot,marks,negative_marks,options_snapshot,correct_option_ids,selected_option_ids FROM quiz_attempt_questions WHERE attempt_id=? ORDER BY display_order",
                (rs, row) -> new AttemptQuestionRow(rs.getObject("id", UUID.class), null, rs.getString("question_type"), rs.getString("topic_snapshot"), rs.getBigDecimal("marks"), rs.getBigDecimal("negative_marks"), parseOptions(rs.getString("options_snapshot")), parseIds(rs.getString("correct_option_ids")), parseIds(rs.getString("selected_option_ids")), false, false, null), attemptId);
        int correctCount = 0, incorrectCount = 0, unanswered = 0;
        BigDecimal score = BigDecimal.ZERO;
        Map<String, TopicAccumulator> byTopic = new LinkedHashMap<>();
        for (AttemptQuestionRow question : questions) {
            Set<UUID> answer = new HashSet<>(question.selected);
            Set<UUID> correct = new HashSet<>(question.correct);
            boolean attempted = !answer.isEmpty();
            boolean isCorrect = attempted && answer.equals(correct);
            BigDecimal awarded = BigDecimal.ZERO;
            if (!attempted) unanswered++;
            else if (isCorrect) { correctCount++; awarded = question.marks; }
            else { incorrectCount++; awarded = question.negativeMarks.negate(); }
            score = score.add(awarded);
            jdbc.update("UPDATE quiz_attempt_questions SET is_correct=?,awarded_marks=? WHERE id=?", isCorrect, awarded, question.id);
            TopicAccumulator topic = byTopic.computeIfAbsent(question.topic, ignored -> new TopicAccumulator());
            topic.total++; topic.possible = topic.possible.add(question.marks); topic.score = topic.score.add(awarded);
            if (isCorrect) topic.correct++;
        }
        int attempted = correctCount + incorrectCount;
        BigDecimal accuracy = attempted == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(correctCount * 100.0 / attempted).setScale(2, RoundingMode.HALF_UP);
        jdbc.update("UPDATE quiz_attempts SET status='SUBMITTED',submitted_at=?,score=?,correct_count=?,incorrect_count=?,unanswered_count=?,accuracy=? WHERE id=? AND status='IN_PROGRESS'",
                timestamp(submittedAt), score, correctCount, incorrectCount, unanswered, accuracy, attemptId);
    }

    private AttemptView renderAttempt(UUID userId, UUID attemptId) {
        AttemptRecord record = attemptRecord(userId, attemptId, false);
        List<AttemptQuestionRow> rows = jdbc.query("SELECT id,question_id,question_type,topic_snapshot,marks,negative_marks,options_snapshot,correct_option_ids,selected_option_ids,visited,marked_for_review,is_correct,awarded_marks,question_text_snapshot,explanation_snapshot,source_reference_snapshot FROM quiz_attempt_questions WHERE attempt_id=? ORDER BY display_order",
                (rs, row) -> new AttemptQuestionRow(rs.getObject("id", UUID.class), rs.getObject("question_id", UUID.class), rs.getString("question_type"), rs.getString("topic_snapshot"), rs.getBigDecimal("marks"), rs.getBigDecimal("negative_marks"), parseOptions(rs.getString("options_snapshot")), parseIds(rs.getString("correct_option_ids")), parseIds(rs.getString("selected_option_ids")), rs.getBoolean("visited"), rs.getBoolean("marked_for_review"), (Boolean) rs.getObject("is_correct"), rs.getString("question_text_snapshot"), rs.getString("explanation_snapshot"), rs.getString("source_reference_snapshot"), rs.getBigDecimal("awarded_marks")), attemptId);
        boolean submitted = record.status.equals("SUBMITTED");
        List<AttemptQuestion> questions = rows.stream().map((row) -> new AttemptQuestion(row.id, rows.indexOf(row) + 1, row.text, row.type, row.topic, row.marks,
                row.options.stream().map(option -> new OptionView(option.id(), option.label(), option.text(), null)).toList(), row.selected, row.visited, row.marked,
                submitted ? row.correctFlag : null, submitted ? row.correct : null, submitted ? row.awarded : null,
                submitted ? row.explanation : null, submitted ? row.sourceReference : null)).toList();
        List<TopicResult> topics = submitted ? topicResults(rows) : List.of();
        long seconds = record.submittedAt == null ? Math.max(0, Duration.between(record.startedAt, clock.instant()).getSeconds()) : Math.max(0, Duration.between(record.startedAt, record.submittedAt).getSeconds());
        return new AttemptView(record.id, record.quizId, record.title, record.examType, record.subject, record.status, record.totalQuestions,
                record.totalMarks, record.score, record.correctCount, record.incorrectCount, record.unansweredCount, record.accuracy,
                record.startedAt, record.expiresAt, record.submittedAt, seconds, questions, topics);
    }

    private List<TopicResult> topicResults(List<AttemptQuestionRow> rows) {
        Map<String, TopicAccumulator> topics = new LinkedHashMap<>();
        for (AttemptQuestionRow row : rows) {
            TopicAccumulator item = topics.computeIfAbsent(row.topic, ignored -> new TopicAccumulator()); item.total++; item.possible = item.possible.add(row.marks);
            item.score = item.score.add(row.awarded == null ? BigDecimal.ZERO : row.awarded); if (Boolean.TRUE.equals(row.correctFlag)) item.correct++;
        }
        return topics.entrySet().stream().map(entry -> {
            TopicAccumulator item = entry.getValue();
            BigDecimal accuracy = item.total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(item.correct * 100.0 / item.total).setScale(2, RoundingMode.HALF_UP);
            return new TopicResult(entry.getKey(), item.total, item.correct, item.score, item.possible, accuracy);
        }).toList();
    }

    private AttemptRecord attemptRecord(UUID userId, UUID attemptId, boolean lock) {
        String sql = "SELECT id,quiz_id,quiz_title_snapshot,exam_type,subject,total_questions,total_marks,score,correct_count,incorrect_count,unanswered_count,accuracy,started_at,expires_at,submitted_at,status FROM quiz_attempts WHERE id=? AND user_id=?" + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, row) -> new AttemptRecord(rs.getObject("id", UUID.class), rs.getObject("quiz_id", UUID.class), rs.getString("quiz_title_snapshot"), rs.getString("exam_type"), rs.getString("subject"), rs.getInt("total_questions"), rs.getBigDecimal("total_marks"), rs.getBigDecimal("score"), (Integer) rs.getObject("correct_count"), (Integer) rs.getObject("incorrect_count"), (Integer) rs.getObject("unanswered_count"), rs.getBigDecimal("accuracy"), instant(rs, "started_at"), instant(rs, "expires_at"), nullableInstant(rs, "submitted_at"), rs.getString("status")), attemptId, userId).stream().findFirst().orElseThrow(() -> notFound("Quiz attempt not found."));
    }

    private AttemptQuestionRow attemptQuestion(UUID attemptId, UUID questionAttemptId) {
        return jdbc.query("SELECT id,question_type,topic_snapshot,marks,negative_marks,options_snapshot,correct_option_ids,selected_option_ids,visited,marked_for_review FROM quiz_attempt_questions WHERE id=? AND attempt_id=?",
                (rs, row) -> new AttemptQuestionRow(rs.getObject("id", UUID.class), null, rs.getString("question_type"), rs.getString("topic_snapshot"), rs.getBigDecimal("marks"), rs.getBigDecimal("negative_marks"), parseOptions(rs.getString("options_snapshot")), parseIds(rs.getString("correct_option_ids")), parseIds(rs.getString("selected_option_ids")), rs.getBoolean("visited"), rs.getBoolean("marked_for_review"), null), questionAttemptId, attemptId).stream().findFirst().orElseThrow(() -> notFound("Question does not belong to this attempt."));
    }

    private QuizConfig resolveConfig(StartRequest request) {
        if (request.quizId() != null) {
            return jdbc.query("SELECT z.id,z.title,z.exam_type,z.subject,z.topic,z.difficulty,z.duration_minutes,(SELECT COUNT(*) FROM quiz_questions q WHERE q.quiz_id=z.id AND q.status='PUBLISHED') question_count FROM quizzes z WHERE z.id=? AND z.status='PUBLISHED'",
                    (rs, row) -> new QuizConfig(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("exam_type"), rs.getString("subject"), rs.getString("topic"), rs.getString("difficulty"), rs.getInt("duration_minutes"), rs.getInt("question_count")), request.quizId())
                    .stream().findFirst().orElseThrow(() -> notFound("Quiz not found."));
        }
        String exam = normalized(request.examType()); String subject = blank(request.subject());
        if (exam == null || !EXAMS.contains(exam)) throw badRequest("Select GATE_CE, SSC_JE_CE, or GENERAL_CIVIL.");
        if (subject == null) throw badRequest("Subject is required for custom practice.");
        String difficulty = normalized(request.difficulty());
        if (difficulty != null && !DIFFICULTIES.contains(difficulty)) throw badRequest("Select a supported difficulty.");
        return new QuizConfig(null, null, exam, subject, blank(request.topic()), difficulty, request.durationMinutes() == null ? 15 : request.durationMinutes(), request.questionCount() == null ? 10 : request.questionCount());
    }

    private SelectedQuestion selectedQuestion(ResultSet rs) throws SQLException { return new SelectedQuestion(rs.getObject("id", UUID.class), rs.getString("question_text"), rs.getString("question_type"), rs.getString("topic"), rs.getBigDecimal("marks"), rs.getBigDecimal("negative_marks"), rs.getString("explanation"), rs.getString("source_reference")); }
    private List<OptionView> options(UUID questionId, boolean includeCorrect) {
        return jdbc.query("SELECT id,option_label,option_text,is_correct FROM quiz_options WHERE question_id=? ORDER BY display_order",
                (rs, row) -> new OptionView(rs.getObject("id", UUID.class), rs.getString("option_label"), rs.getString("option_text"), includeCorrect ? rs.getBoolean("is_correct") : null), questionId);
    }
    private List<UUID> correctOptionIds(UUID questionId) { return jdbc.query("SELECT id FROM quiz_options WHERE question_id=? AND is_correct=TRUE ORDER BY display_order", (rs, row) -> rs.getObject("id", UUID.class), questionId); }

    private static QuizSummary quizSummary(ResultSet rs) throws SQLException { return new QuizSummary(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("exam_type"), rs.getString("subject"), rs.getString("topic"), rs.getString("difficulty"), rs.getInt("duration_minutes"), rs.getInt("question_count"), rs.getBigDecimal("total_marks"), rs.getString("status")); }
    private List<OptionView> parseOptions(String text) { try { List<Map<String,Object>> values = json.readValue(text, OPTION_JSON); return values.stream().map(value -> new OptionView(UUID.fromString((String)value.get("id")), (String)value.get("label"), (String)value.get("text"), null)).toList(); } catch (Exception exception) { throw new IllegalStateException("Could not read quiz options snapshot.", exception); } }
    private List<UUID> parseIds(String text) { try { return json.readValue(text, ID_JSON).stream().map(UUID::fromString).toList(); } catch (Exception exception) { throw new IllegalStateException("Could not read quiz answer snapshot.", exception); } }
    private String writeJson(Object value) { try { return json.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException("Could not save quiz snapshot.", exception); } }
    private static Instant instant(ResultSet rs, String column) throws SQLException { return rs.getObject(column, OffsetDateTime.class).toInstant(); }
    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException { OffsetDateTime value = rs.getObject(column, OffsetDateTime.class); return value == null ? null : value.toInstant(); }
    private static OffsetDateTime timestamp(Instant value) { return OffsetDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static String normalized(String value) { return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT); }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    private record QuizConfig(UUID quizId, String title, String examType, String subject, String topic, String difficulty, int durationMinutes, Integer defaultQuestionCount) { }
    private record SelectedQuestion(UUID id, String text, String type, String topic, BigDecimal marks, BigDecimal negativeMarks, String explanation, String sourceReference) { }
    private record AttemptRecord(UUID id, UUID quizId, String title, String examType, String subject, int totalQuestions, BigDecimal totalMarks, BigDecimal score, Integer correctCount, Integer incorrectCount, Integer unansweredCount, BigDecimal accuracy, Instant startedAt, Instant expiresAt, Instant submittedAt, String status) { }
    private record AttemptQuestionRow(UUID id, UUID questionId, String type, String topic, BigDecimal marks, BigDecimal negativeMarks, List<OptionView> options, List<UUID> correct, List<UUID> selected, boolean visited, boolean marked, Boolean correctFlag, String text, String explanation, String sourceReference, BigDecimal awarded) {
        private AttemptQuestionRow(UUID id, UUID questionId, String type, String topic, BigDecimal marks, BigDecimal negativeMarks, List<OptionView> options, List<UUID> correct, List<UUID> selected, boolean visited, boolean marked, Boolean correctFlag) { this(id, questionId, type, topic, marks, negativeMarks, options, correct, selected, visited, marked, correctFlag, null, null, null, null); }
    }
    private static final class TopicAccumulator { int total; int correct; BigDecimal score = BigDecimal.ZERO; BigDecimal possible = BigDecimal.ZERO; }
}
