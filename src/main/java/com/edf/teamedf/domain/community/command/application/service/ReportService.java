package com.edf.teamedf.domain.community.command.application.service;

import com.edf.teamedf.domain.community.command.application.dto.report.ReportCreateRequest;
import com.edf.teamedf.domain.community.command.application.dto.report.ReportProcessRequest;
import com.edf.teamedf.domain.community.command.application.dto.report.ReportResponse;
import com.edf.teamedf.domain.community.command.domain.Comment;
import com.edf.teamedf.domain.community.command.domain.Post;
import com.edf.teamedf.domain.community.command.domain.Report;
import com.edf.teamedf.domain.community.command.infrastructure.CommentRepository;
import com.edf.teamedf.domain.community.command.infrastructure.PostRepository;
import com.edf.teamedf.domain.community.command.infrastructure.ReportRepository;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * 커뮤니티 신고 접수 및 운영자 처리.
 * (Google Play UGC 정책: 앱 내 콘텐츠/사용자 신고 + 운영자가 조치할 수 있는 절차)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    /**
     * 신고 접수. 같은 대상을 다시 신고하면 새로 만들지 않고 기존 신고를 그대로 돌려준다
     * (앱이 실패한 신고를 재전송해도 중복이 생기지 않도록).
     */
    @Transactional
    public ReportResponse createReport(Long reporterId, ReportCreateRequest request) {
        Optional<Report> existing = reportRepository.findByReporter_UserIdAndTargetTypeAndTargetId(
                reporterId, request.targetType(), request.targetId());
        if (existing.isPresent()) {
            return ReportResponse.from(existing.get());
        }

        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Long targetUserId;
        Long postId = request.postId();
        switch (request.targetType()) {
            case POST -> {
                Post post = postRepository.findById(request.targetId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."));
                targetUserId = post.getUser() == null ? null : post.getUser().getUserId();
                postId = post.getPostId();
            }
            case COMMENT -> {
                Comment comment = commentRepository.findById(request.targetId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."));
                targetUserId = comment.getUser() == null ? null : comment.getUser().getUserId();
                postId = comment.getPost() == null ? postId : comment.getPost().getPostId();
            }
            case USER -> {
                if (!userRepository.existsById(request.targetId())) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
                }
                targetUserId = request.targetId();
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 신고 대상입니다.");
        }

        if (reporterId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자신의 콘텐츠나 계정은 신고할 수 없습니다.");
        }

        String detail = request.detail() == null ? null : request.detail().trim();
        if (request.reason() == Report.Reason.OTHER && (detail == null || detail.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기타 사유는 상세 내용을 입력해야 합니다.");
        }

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(request.targetType())
                .targetId(request.targetId())
                .targetUserId(targetUserId)
                .postId(postId)
                .reason(request.reason())
                .detail(detail == null || detail.isEmpty() ? null : detail)
                .build();
        return ReportResponse.from(reportRepository.save(report));
    }

    // ==================== 운영자 ====================

    /** 신고 목록 (최신순). status 가 null 이면 전체. */
    public Page<ReportResponse> getReports(Report.Status status, Pageable pageable) {
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<Report> reports = status == null
                ? reportRepository.findAllByOrderByCreatedAtDesc(page)
                : reportRepository.findAllByStatusOrderByCreatedAtDesc(status, page);
        return reports.map(ReportResponse::from);
    }

    /** 신고 처리. RESOLVED + hideContent 이면 신고된 게시글/댓글을 숨긴다. */
    @Transactional
    public ReportResponse processReport(Long adminId, Long reportId, ReportProcessRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다."));

        if (request.status() == Report.Status.RESOLVED && Boolean.TRUE.equals(request.hideContent())) {
            hideTarget(report);
        }
        report.process(request.status(), request.adminMemo(), adminId);
        return ReportResponse.from(report);
    }

    private void hideTarget(Report report) {
        switch (report.getTargetType()) {
            case POST -> postRepository.findById(report.getTargetId()).ifPresent(Post::delete);
            case COMMENT -> commentRepository.findById(report.getTargetId()).ifPresent(Comment::delete);
            case USER -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "사용자 신고는 숨길 콘텐츠가 없습니다. 게시글/댓글 신고에서 처리해 주세요.");
        }
    }
}
