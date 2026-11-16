package com.civilmate.api.flashcards;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.civilmate.api.flashcards.FlashcardDtos.*;

@Service
public class FlashcardService {
    private static final Set<String> DIFFICULTIES = Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");
    private static final Set<String> STATUSES = Set.of("DRAFT", "REVIEW", "PUBLISHED", "ARCHIVED");
    private final JdbcTemplate jdbc;
    public FlashcardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<Category> categories() { return jdbc.query("SELECT id,name,slug,description FROM flashcard_categories ORDER BY name", (rs, n) -> new Category(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("slug"), rs.getString("description"))); }

    @Transactional(readOnly = true)
    public Page cards(UUID user, String category, String difficulty, String status, boolean revision, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE f.status='PUBLISHED'");
        List<Object> args = new ArrayList<>();
        if (category != null && !category.isBlank()) { where.append(" AND c.slug=?"); args.add(category); }
        if (difficulty != null && !difficulty.isBlank()) { String selected = difficulty.toUpperCase(Locale.ROOT); if (!DIFFICULTIES.contains(selected)) throw bad("Unsupported difficulty."); where.append(" AND f.difficulty=?"); args.add(selected); }
        if (revision) { where.append(" AND p.status='REVISION'"); }
        if ("bookmarks".equals(status)) where.append(" AND p.bookmarked=TRUE");
        if (status != null && Set.of("KNOWN", "REVISION").contains(status)) { where.append(" AND p.status=?"); args.add(status); }
        String join = " FROM flashcards f JOIN flashcard_categories c ON c.id=f.category_id LEFT JOIN flashcard_progress p ON p.flashcard_id=f.id AND p.user_id=?";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + join + where, Long.class, prepend(user, args).toArray());
        List<Object> queryArgs = new ArrayList<>(prepend(user, args)); queryArgs.add(size); queryArgs.add((long) page * size);
        List<Card> content = jdbc.query("SELECT f.id,f.category_id,c.name category,c.slug category_slug,f.question,f.short_answer,f.detailed_explanation,f.topic,f.difficulty,f.related_formula,f.related_laboratory_test,f.source_reference,f.status content_status,COALESCE(p.status,'NEW') progress_status,COALESCE(p.bookmarked,FALSE) bookmarked,COALESCE(p.review_count,0) review_count,p.last_reviewed_at" + join + where + " ORDER BY c.name,f.topic,f.id LIMIT ? OFFSET ?", (rs,n)->card(rs), queryArgs.toArray());
        long count = total == null ? 0 : total;
        return new Page(content, page, size, count, (int)Math.ceil((double)count/size));
    }

    @Transactional(readOnly = true)
    public Card card(UUID user, UUID id) {
        return jdbc.query("SELECT f.id,f.category_id,c.name category,c.slug category_slug,f.question,f.short_answer,f.detailed_explanation,f.topic,f.difficulty,f.related_formula,f.related_laboratory_test,f.source_reference,f.status content_status,COALESCE(p.status,'NEW') progress_status,COALESCE(p.bookmarked,FALSE) bookmarked,COALESCE(p.review_count,0) review_count,p.last_reviewed_at FROM flashcards f JOIN flashcard_categories c ON c.id=f.category_id LEFT JOIN flashcard_progress p ON p.flashcard_id=f.id AND p.user_id=? WHERE f.id=? AND f.status='PUBLISHED'", (rs,n)->card(rs), user,id).stream().findFirst().orElseThrow(()->notFound("Flashcard not found."));
    }

    @Transactional
    public Card progress(UUID user, UUID id, ProgressWrite request) {
        String state = request.status().toUpperCase(Locale.ROOT);
        if (!Set.of("NEW", "KNOWN", "REVISION").contains(state)) throw bad("Progress status must be NEW, KNOWN, or REVISION.");
        if (!Boolean.TRUE.equals(jdbc.query("SELECT EXISTS(SELECT 1 FROM flashcards WHERE id=? AND status='PUBLISHED')", rs -> rs.next() && rs.getBoolean(1), id))) throw notFound("Flashcard not found.");
        boolean reviewed = !Boolean.FALSE.equals(request.reviewed()) && !"NEW".equals(state);
        jdbc.update("INSERT INTO flashcard_progress(id,user_id,flashcard_id,status,bookmarked,last_reviewed_at,review_count) VALUES (?,?,?,?,?,CASE WHEN ? THEN now() ELSE NULL END,CASE WHEN ? THEN 1 ELSE 0 END) ON CONFLICT(user_id,flashcard_id) DO UPDATE SET status=CASE WHEN ? THEN EXCLUDED.status ELSE flashcard_progress.status END,bookmarked=CASE WHEN ? IS NULL THEN flashcard_progress.bookmarked ELSE EXCLUDED.bookmarked END,last_reviewed_at=CASE WHEN ? THEN now() ELSE flashcard_progress.last_reviewed_at END,review_count=flashcard_progress.review_count+CASE WHEN ? THEN 1 ELSE 0 END,updated_at=now()",
                UUID.randomUUID(),user,id,state,Boolean.TRUE.equals(request.bookmarked()),reviewed,reviewed,reviewed,request.bookmarked(),reviewed,reviewed);
        return card(user,id);
    }

    @Transactional(readOnly = true)
    public Progress summary(UUID user) {
        long studied = scalar("SELECT COUNT(*) FROM flashcard_progress WHERE user_id=? AND review_count>0", user);
        long known = scalar("SELECT COUNT(*) FROM flashcard_progress WHERE user_id=? AND status='KNOWN'", user);
        long revision = scalar("SELECT COUNT(*) FROM flashcard_progress WHERE user_id=? AND status='REVISION'", user);
        long bookmarked = scalar("SELECT COUNT(*) FROM flashcard_progress WHERE user_id=? AND bookmarked", user);
        List<Card> recent = jdbc.query("SELECT f.id,f.category_id,c.name category,c.slug category_slug,f.question,f.short_answer,f.detailed_explanation,f.topic,f.difficulty,f.related_formula,f.related_laboratory_test,f.source_reference,f.status content_status,p.status progress_status,p.bookmarked,p.review_count,p.last_reviewed_at FROM flashcard_progress p JOIN flashcards f ON f.id=p.flashcard_id JOIN flashcard_categories c ON c.id=f.category_id WHERE p.user_id=? AND f.status='PUBLISHED' AND p.review_count>0 ORDER BY p.last_reviewed_at DESC LIMIT 8", (rs,n)->card(rs), user);
        List<CategoryProgress> byCategory = jdbc.query("SELECT c.name,COUNT(p.flashcard_id) FILTER (WHERE p.review_count>0) studied,COUNT(p.flashcard_id) FILTER (WHERE p.status='KNOWN') known,COUNT(p.flashcard_id) FILTER (WHERE p.status='REVISION') revision,COUNT(f.id) total FROM flashcard_categories c LEFT JOIN flashcards f ON f.category_id=c.id AND f.status='PUBLISHED' LEFT JOIN flashcard_progress p ON p.flashcard_id=f.id AND p.user_id=? GROUP BY c.id,c.name ORDER BY c.name", (rs,n)->new CategoryProgress(rs.getString(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getLong(5)), user);
        return new Progress(studied,known,revision,bookmarked,recent,byCategory);
    }

    @Transactional(readOnly = true)
    public Page adminCards(String status, int page, int size) {
        String selected = status == null || status.isBlank() ? null : checkedStatus(status);
        String where = selected == null ? "" : " WHERE f.status=?";
        Long total = selected == null ? jdbc.queryForObject("SELECT COUNT(*) FROM flashcards", Long.class) : jdbc.queryForObject("SELECT COUNT(*) FROM flashcards f"+where,Long.class,selected);
        String sql = "SELECT f.id,f.category_id,c.name category,c.slug category_slug,f.question,f.short_answer,f.detailed_explanation,f.topic,f.difficulty,f.related_formula,f.related_laboratory_test,f.source_reference,f.status content_status,'NEW' progress_status,FALSE bookmarked,0 review_count,NULL::timestamptz last_reviewed_at FROM flashcards f JOIN flashcard_categories c ON c.id=f.category_id"+where+" ORDER BY f.updated_at DESC LIMIT ? OFFSET ?";
        List<Object> args = new ArrayList<>(); if(selected!=null) args.add(selected); args.add(size);args.add((long)page*size);
        long count=total==null?0:total; return new Page(jdbc.query(sql,(rs,n)->card(rs),args.toArray()),page,size,count,(int)Math.ceil((double)count/size));
    }

    @Transactional
    public Category createCategory(CategoryWrite r) { UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO flashcard_categories(id,name,slug,description) VALUES (?,?,?,?)",id,r.name().trim(),slug(r.slug()),r.description()); return categories().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow(); }
    @Transactional
    public Category updateCategory(UUID id,CategoryWrite r) { if(categories().stream().noneMatch(c->c.id().equals(id)))throw notFound("Flashcard category not found.");jdbc.update("UPDATE flashcard_categories SET name=?,slug=?,description=?,updated_at=now() WHERE id=?",r.name().trim(),slug(r.slug()),r.description(),id);return categories().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow(); }
    @Transactional
    public void deleteCategory(UUID id) { Long used=jdbc.queryForObject("SELECT COUNT(*) FROM flashcards WHERE category_id=?",Long.class,id);if(used!=null&&used>0)throw bad("Category has flashcards. Reassign or archive them before deleting this category.");int deleted=jdbc.update("DELETE FROM flashcard_categories WHERE id=?",id);if(deleted==0)throw notFound("Flashcard category not found."); }
    @Transactional
    public Card createCard(CardWrite r) { validate(r); UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO flashcards(id,category_id,question,short_answer,detailed_explanation,topic,difficulty,related_formula,related_laboratory_test,source_reference,status) VALUES (?,?,?,?,?,?,?,?,?,?,?)",id,r.categoryId(),r.question().trim(),r.shortAnswer().trim(),r.detailedExplanation().trim(),r.topic().trim(),r.difficulty().toUpperCase(Locale.ROOT),r.relatedFormula(),r.relatedLaboratoryTest(),r.sourceReference().trim(),r.status().toUpperCase(Locale.ROOT)); return adminCard(id); }
    @Transactional
    public Card updateCard(UUID id, CardWrite r) { validate(r); if(!exists(id)) throw notFound("Flashcard not found."); jdbc.update("UPDATE flashcards SET category_id=?,question=?,short_answer=?,detailed_explanation=?,topic=?,difficulty=?,related_formula=?,related_laboratory_test=?,source_reference=?,status=?,updated_at=now() WHERE id=?",r.categoryId(),r.question().trim(),r.shortAnswer().trim(),r.detailedExplanation().trim(),r.topic().trim(),r.difficulty().toUpperCase(Locale.ROOT),r.relatedFormula(),r.relatedLaboratoryTest(),r.sourceReference().trim(),r.status().toUpperCase(Locale.ROOT),id); return adminCard(id); }
    @Transactional
    public void archive(UUID id) { if(!exists(id)) throw notFound("Flashcard not found."); jdbc.update("UPDATE flashcards SET status='ARCHIVED',updated_at=now() WHERE id=?",id); }
    private Card adminCard(UUID id) { return jdbc.query("SELECT f.id,f.category_id,c.name category,c.slug category_slug,f.question,f.short_answer,f.detailed_explanation,f.topic,f.difficulty,f.related_formula,f.related_laboratory_test,f.source_reference,f.status content_status,'NEW' progress_status,FALSE bookmarked,0 review_count,NULL::timestamptz last_reviewed_at FROM flashcards f JOIN flashcard_categories c ON c.id=f.category_id WHERE f.id=?",(rs,n)->card(rs),id).stream().findFirst().orElseThrow(); }
    private boolean exists(UUID id) { return Boolean.TRUE.equals(jdbc.query("SELECT EXISTS(SELECT 1 FROM flashcards WHERE id=?)",rs->rs.next()&&rs.getBoolean(1),id)); }
    private long scalar(String sql, UUID user) { Long n=jdbc.queryForObject(sql,Long.class,user);return n==null?0:n; }
    private void validate(CardWrite r) { String d=r.difficulty().toUpperCase(Locale.ROOT),s=checkedStatus(r.status()); if(!DIFFICULTIES.contains(d)) throw bad("Unsupported difficulty."); if(!Boolean.TRUE.equals(jdbc.query("SELECT EXISTS(SELECT 1 FROM flashcard_categories WHERE id=?)",rs->rs.next()&&rs.getBoolean(1),r.categoryId()))) throw bad("Flashcard category not found."); if("PUBLISHED".equals(s)&&r.sourceReference().isBlank()) throw bad("Published flashcards require a source."); }
    private static String checkedStatus(String s) { String v=s.toUpperCase(Locale.ROOT);if(!STATUSES.contains(v))throw bad("Unsupported content status.");return v; }
    private static String slug(String s) { String v=s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");if(v.isBlank())throw bad("Category slug is required.");return v; }
    private static List<Object> prepend(UUID user,List<Object> args) { List<Object> values=new ArrayList<>();values.add(user);values.addAll(args);return values; }
    private static Card card(ResultSet rs) throws SQLException { var t=rs.getTimestamp("last_reviewed_at");return new Card(rs.getObject("id",UUID.class),rs.getObject("category_id",UUID.class),rs.getString("category"),rs.getString("category_slug"),rs.getString("question"),rs.getString("short_answer"),rs.getString("detailed_explanation"),rs.getString("topic"),rs.getString("difficulty"),rs.getString("related_formula"),rs.getString("related_laboratory_test"),rs.getString("source_reference"),rs.getString("progress_status"),rs.getBoolean("bookmarked"),rs.getInt("review_count"),t==null?null:t.toInstant(),rs.getString("content_status")); }
    private static ResponseStatusException bad(String s) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,s); }
    private static ResponseStatusException notFound(String s) { return new ResponseStatusException(HttpStatus.NOT_FOUND,s); }
}
