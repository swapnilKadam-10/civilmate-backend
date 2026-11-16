package com.civilmate.api.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.civilmate.api.admin.AdminDtos.*;

@Service
public class AdminService {
    private final JdbcTemplate jdbc;
    public AdminService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(readOnly=true)
    public UserPage users(String query,String role,int page,int size) {
        String search=query==null||query.isBlank()?null:"%"+query.trim().toLowerCase(Locale.ROOT)+"%";
        String selected=role==null||role.isBlank()?null:role.trim().toUpperCase(Locale.ROOT);
        if(selected!=null&&!Set.of("USER","ADMIN").contains(selected)) throw bad("Role must be USER or ADMIN.");
        String where=" WHERE (CAST(? AS text) IS NULL OR role=?) AND (CAST(? AS text) IS NULL OR lower(full_name) LIKE ? OR lower(email) LIKE ?)";
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM users"+where,Long.class,selected,selected,search,search,search);
        List<UserRow> rows=jdbc.query("SELECT id,full_name,email,role,active,created_at,last_login_at FROM users"+where+" ORDER BY created_at DESC LIMIT ? OFFSET ?",(rs,n)->user(rs),selected,selected,search,search,search,size,(long)page*size);
        long count=total==null?0:total;return new UserPage(rows,page,size,count,(int)Math.ceil((double)count/size));
    }

    @Transactional
    public UserRow updateUser(UUID actor,UUID id,UserUpdate request) {
        String role=request.role().trim().toUpperCase(Locale.ROOT);
        if(!Set.of("USER","ADMIN").contains(role)) throw bad("Role must be USER or ADMIN.");
        UserRow target=jdbc.query("SELECT id,full_name,email,role,active,created_at,last_login_at FROM users WHERE id=?",(rs,n)->user(rs),id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found."));
        boolean active=request.active();
        if(id.equals(actor)&&(!active||!"ADMIN".equals(role))) throw bad("You cannot deactivate or demote your own administrator account.");
        if("ADMIN".equals(target.role())&&target.active()&&(!active||!"ADMIN".equals(role))) {
            Long others=jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN' AND active=TRUE AND id<>?",Long.class,id);
            if(others==null||others==0) throw bad("At least one active administrator must remain.");
        }
        jdbc.update("UPDATE users SET role=?,active=?,updated_at=now() WHERE id=?",role,active,id);
        if(!active) jdbc.update("UPDATE refresh_tokens SET revoked_at=now() WHERE user_id=? AND revoked_at IS NULL",id);
        return jdbc.query("SELECT id,full_name,email,role,active,created_at,last_login_at FROM users WHERE id=?",(rs,n)->user(rs),id).get(0);
    }

    @Transactional(readOnly=true)
    public Analytics analytics() {
        long users=number("SELECT COUNT(*) FROM users");
        long active=number("SELECT COUNT(*) FROM users WHERE active=TRUE AND last_login_at>=now()-interval '30 days'");
        long attempts=number("SELECT COUNT(*) FROM quiz_attempts WHERE status='SUBMITTED'");
        Double accuracy=jdbc.queryForObject("SELECT COALESCE(AVG(accuracy),0) FROM quiz_attempts WHERE status='SUBMITTED' AND accuracy IS NOT NULL",Double.class);
        long reviews=number("SELECT COALESCE(SUM(review_count),0) FROM flashcard_progress");
        long learners=number("SELECT COUNT(DISTINCT user_id) FROM flashcard_progress WHERE review_count>0");
        List<PopularRow> subjects=jdbc.query("SELECT subject,COUNT(*) FROM quiz_attempts WHERE status='SUBMITTED' GROUP BY subject ORDER BY COUNT(*) DESC,subject LIMIT 5",(rs,n)->new PopularRow(rs.getString(1),rs.getLong(2)));
        List<PopularRow> quizzes=jdbc.query("SELECT quiz_title_snapshot,COUNT(*) FROM quiz_attempts WHERE status='SUBMITTED' GROUP BY quiz_title_snapshot ORDER BY COUNT(*) DESC,quiz_title_snapshot LIMIT 5",(rs,n)->new PopularRow(rs.getString(1),rs.getLong(2)));
        return new Analytics(users,active,attempts,accuracy==null?0:Math.round(accuracy*10.0)/10.0,reviews,learners,subjects,quizzes);
    }
    private long number(String sql) { Long value=jdbc.queryForObject(sql,Long.class);return value==null?0:value; }
    private static UserRow user(ResultSet rs)throws SQLException { var created=rs.getTimestamp("created_at");var login=rs.getTimestamp("last_login_at");return new UserRow(rs.getObject("id",UUID.class),rs.getString("full_name"),rs.getString("email"),rs.getString("role"),rs.getBoolean("active"),created.toInstant(),login==null?null:login.toInstant()); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
