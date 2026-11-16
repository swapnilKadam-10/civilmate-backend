package com.civilmate.api.admin;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatusCode;
import com.civilmate.api.admin.AdminDtos.*;
import com.civilmate.api.auth.dto.ApiEnvelope;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService admin;
    public AdminController(AdminService admin) { this.admin=admin; }
    @GetMapping("/users") public ApiEnvelope<UserPage> users(@RequestParam(required=false) String q,@RequestParam(required=false) String role,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="25") @Min(1) @Max(100) int size) { return ApiEnvelope.success("Users retrieved.",admin.users(q,role,page,size)); }
    @PatchMapping("/users/{id}") public ApiEnvelope<UserRow> updateUser(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody UserUpdate request) { return ApiEnvelope.success("User updated.",admin.updateUser(userId(jwt),id,request)); }
    @GetMapping("/analytics") public ApiEnvelope<Analytics> analytics() { return ApiEnvelope.success("Admin analytics retrieved.",admin.analytics()); }
    private static UUID userId(Jwt jwt) { try { return UUID.fromString(jwt.getSubject()); } catch(RuntimeException ex) { throw new ResponseStatusException(HttpStatusCode.valueOf(401),"Invalid access token."); } }
}
