package com.templestamp.user;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.ItemsResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.global.type.AgreementType;
import com.templestamp.user.dto.AccountDeleteRequest;
import com.templestamp.user.dto.AgreementRequest;
import com.templestamp.user.dto.AgreementResponse;
import com.templestamp.user.dto.UserResponse;
import com.templestamp.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(userService.getMe(user.userId()));
    }

    /**
     * 회원 탈퇴. 비밀번호를 다시 받는다 — 되돌릴 수 없는 일이고, 세션이 살아 있다는 것만으로는
     * 근거가 되지 않는다(자리를 비운 사이 남이 누를 수 있다). 틀리면 로그인과 같은 코드·같은 잠금.
     * <p>
     * 응답은 204 다. 지운 것을 다시 실어 보낼 이유가 없고, 이후 그 토큰들은 전부 401 이 된다.
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal AuthenticatedUser user,
                         @Valid @RequestBody AccountDeleteRequest request) {
        userService.deleteAccount(user.userId(), request.password());
    }

    /** 보낸 필드만 바뀐다. tier 도 여기서 사용자가 직접 고른다. */
    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateMe(@AuthenticationPrincipal AuthenticatedUser user,
                                              @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(userService.updateMe(user.userId(), request));
    }

    @GetMapping("/me/agreements")
    public ApiResponse<ItemsResponse<AgreementResponse>> getAgreements(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(ItemsResponse.of(userService.getAgreements(user.userId())));
    }

    @PostMapping("/me/agreements")
    public ApiResponse<ItemsResponse<AgreementResponse>> agree(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @NotEmpty(message = "동의 항목이 비어 있습니다.") List<@Valid AgreementRequest> requests) {
        return ApiResponse.ok(ItemsResponse.of(userService.agree(user.userId(), requests)));
    }

    /**
     * 철회. 행을 지우지 않고 withdrawn_at 만 채운다.
     * <p>
     * 종류를 경로변수로 받고 본문은 없다. 동의는 가입 직후 여러 개를 한 번에 하므로 POST 가 배열이지만,
     * 철회는 한 건씩 하는 행위라 경로에 대상이 드러나는 편이 맞다. DELETE 에 본문을 싣는 것은
     * 프록시·클라이언트에 따라 버려지기도 해서 호환성이 나쁘다.
     * <p>
     * <b>204 · 본문 없음</b>이다. 예전에는 철회 뒤 남은 목록을 함께 돌려줬는데,
     * 같은 DELETE 가 자리마다 다른 모양을 내고 있었다(감사 G STEP 5). 남은 목록이 필요하면
     * {@code GET /api/users/me/agreements} 를 한 번 더 부른다 — 목록은 목록의 문으로.
     */
    @DeleteMapping("/me/agreements/{agreementType}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal AuthenticatedUser user,
                         @PathVariable AgreementType agreementType) {
        userService.withdraw(user.userId(), agreementType);
    }
}
