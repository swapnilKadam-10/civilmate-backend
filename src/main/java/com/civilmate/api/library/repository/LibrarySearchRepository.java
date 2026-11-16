package com.civilmate.api.library.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.civilmate.api.library.dto.LibraryDtos.CodeCard;
import com.civilmate.api.library.dto.LibraryDtos.FormulaCard;
import com.civilmate.api.library.dto.LibraryDtos.PageView;

@Repository
public class LibrarySearchRepository {
    private final JdbcTemplate jdbc;
    public LibrarySearchRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public PageView<FormulaCard> formulas(String query, String categorySlug, int page, int size, String sort) {
        String condition = " FROM formulas f JOIN categories c ON c.id=f.category_id WHERE f.status='PUBLISHED' AND (CAST(? AS text) IS NULL OR f.search_vector @@ plainto_tsquery('simple', ?) OR lower(c.name)=lower(?) OR lower(c.subject)=lower(?)) AND (CAST(? AS text) IS NULL OR c.slug=?)";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + condition, Long.class, query, query, query, query, categorySlug, categorySlug);
        String order = "recent".equals(sort) ? "f.updated_at DESC, f.title ASC" : "lower(f.title) ASC";
        List<FormulaCard> rows = jdbc.query("SELECT f.id, f.title, f.slug, c.name category, c.slug category_slug, f.topic, f.expression, f.difficulty" + condition + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                (rs, index) -> new FormulaCard(rs.getObject("id", java.util.UUID.class), rs.getString("title"), rs.getString("slug"), rs.getString("category"), rs.getString("category_slug"), rs.getString("topic"), rs.getString("expression"), rs.getString("difficulty")),
                query, query, query, query, categorySlug, categorySlug, size, (long) page * size);
        return new PageView<>(rows, page, size, total, (int) Math.ceil((double) total / size));
    }

    public PageView<CodeCard> codes(String query, String categorySlug, String standard, int page, int size, String sort) {
        String condition = " FROM code_references r JOIN categories c ON c.id=r.category_id WHERE r.status='PUBLISHED' AND (CAST(? AS text) IS NULL OR r.search_vector @@ plainto_tsquery('simple', ?) OR lower(r.code_number)=lower(?)) AND (CAST(? AS text) IS NULL OR c.slug=?) AND (CAST(? AS text) IS NULL OR lower(r.code_number)=lower(?))";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + condition, Long.class, query, query, query, categorySlug, categorySlug, standard, standard);
        String order = "recent".equals(sort) ? "r.updated_at DESC, r.title ASC" : "lower(r.code_number) ASC, lower(r.title) ASC";
        List<CodeCard> rows = jdbc.query("SELECT r.id, r.code_number, r.title, c.name category, c.slug category_slug, r.subject, r.topic, r.edition, r.publication_year, r.verification_level" + condition + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                (rs, index) -> new CodeCard(rs.getObject("id", java.util.UUID.class), rs.getString("code_number"), rs.getString("title"), rs.getString("category"), rs.getString("category_slug"), rs.getString("subject"), rs.getString("topic"), rs.getString("edition"), (Integer) rs.getObject("publication_year"), rs.getString("verification_level")),
                query, query, query, categorySlug, categorySlug, standard, standard, size, (long) page * size);
        return new PageView<>(rows, page, size, total, (int) Math.ceil((double) total / size));
    }
}
