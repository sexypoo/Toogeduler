package app.toogeduler.web;

import app.toogeduler.domain.Group;
import app.toogeduler.repo.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 초대 링크 미리보기. 링크만 있으면 로그인 없이 볼 수 있으므로, 어떤 그룹인지 알아볼 만큼만 돌려준다. 노출 범위: 그룹 이름·색, 멤버 수, 그룹장 표시 이름.
 * 이메일·멤버 목록·일정·그룹 ID 는 넣지 않는다. 가입은 하지 않는다. 가입은 사용자가 "참여하기"를 눌러 POST /api/groups/join/{token} 을 호출할
 * 때만 일어난다.
 */
@RestController
@RequiredArgsConstructor
public class InviteController {
    private final GroupRepository groups;
    private final GroupMemberRepository members;

    @GetMapping("/api/public/invites/{token}")
    @Transactional(readOnly = true)
    Map<String, Object> preview(@PathVariable String token) {
        // 재발급(refresh)·내보내기로 바뀐 옛 토큰은 더 이상 어떤 그룹과도 맞지 않아 404 가 된다.
        Group g =
                groups.findByInviteToken(token)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "유효하지 않은 초대 링크입니다."));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", g.getName());
        out.put("color", g.getColor());
        out.put("memberCount", members.countByGroupId(g.getId()));
        out.put("ownerName", g.getOwner().getName());
        return out;
    }
}
