package com.rinoimob.service.website;

import com.rinoimob.domain.dto.blog.BlogPostResponse;
import com.rinoimob.domain.dto.blog.CreateBlogPostRequest;
import com.rinoimob.domain.dto.blog.PublicBlogPostResponse;
import com.rinoimob.domain.dto.blog.PublicBlogPostSummaryResponse;
import com.rinoimob.domain.dto.blog.UpdateBlogPostRequest;
import com.rinoimob.domain.entity.BlogPost;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.BlogPostStatus;
import com.rinoimob.domain.repository.BlogPostRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BlogPostService {

    private static final Safelist CONTENT_SAFELIST = Safelist.relaxed()
            .addTags("h1", "h2", "h3", "h4", "h5", "h6")
            .addAttributes("a", "target", "rel")
            .addProtocols("a", "href", "http", "https", "mailto", "tel");

    private final BlogPostRepository blogPostRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<BlogPostResponse> list(UUID tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return blogPostRepository.findAllByTenantIdAndDeletedAtIsNull(tenantId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BlogPostResponse get(UUID tenantId, UUID id) {
        return toResponse(findOwned(tenantId, id));
    }

    @Transactional
    public BlogPostResponse create(UUID tenantId, CreateBlogPostRequest request) {
        String sanitizedContent = sanitizeContent(request.contentHtml());
        String excerpt = sanitizeExcerpt(request.excerpt(), sanitizedContent);
        String slug = ensureUniqueSlug(tenantId, request.slug(), request.title(), null);

        BlogPost post = new BlogPost();
        post.setTenantId(tenantId);
        post.setTitle(request.title().trim());
        post.setSlug(slug);
        post.setExcerpt(excerpt);
        post.setContentHtml(sanitizedContent);
        post.setCoverImageUrl(trimToNull(request.coverImageUrl()));

        BlogPostStatus status = request.status() != null ? request.status() : BlogPostStatus.DRAFT;
        post.setStatus(status);

        // Handle customizable published date
        if (status == BlogPostStatus.PUBLISHED) {
            post.setPublishedAt(request.publishedAt() != null ? request.publishedAt() : LocalDateTime.now());
        } else {
            post.setPublishedAt(request.publishedAt());
        }

        // Set author
        UUID currentUserId = TenantContext.getUserId();
        if (currentUserId != null) {
            post.setCreatedBy(currentUserId);
            post.setUpdatedBy(currentUserId);
        }

        return toResponse(blogPostRepository.save(post));
    }

    @Transactional
    public BlogPostResponse update(UUID tenantId, UUID id, UpdateBlogPostRequest request) {
        BlogPost post = findOwned(tenantId, id);

        if (request.title() != null && !request.title().isBlank()) {
            post.setTitle(request.title().trim());
        }
        if (request.slug() != null) {
            post.setSlug(ensureUniqueSlug(tenantId, request.slug(), post.getTitle(), post.getId()));
        } else if (request.title() != null && !request.title().isBlank()) {
            post.setSlug(ensureUniqueSlug(tenantId, null, request.title(), post.getId()));
        }
        if (request.contentHtml() != null) {
            String sanitizedContent = sanitizeContent(request.contentHtml());
            post.setContentHtml(sanitizedContent);
            if (request.excerpt() == null) {
                post.setExcerpt(sanitizeExcerpt(post.getExcerpt(), sanitizedContent));
            }
        }
        if (request.excerpt() != null) {
            post.setExcerpt(sanitizeExcerpt(request.excerpt(), post.getContentHtml()));
        }
        if (request.coverImageUrl() != null) {
            post.setCoverImageUrl(trimToNull(request.coverImageUrl()));
        }
        if (request.status() != null) {
            applyStatus(post, request.status(), request.publishedAt());
        } else if (request.publishedAt() != null) {
            // Allow updating published_at without changing status
            post.setPublishedAt(request.publishedAt());
        }

        // Update author
        UUID currentUserId = TenantContext.getUserId();
        if (currentUserId != null) {
            post.setUpdatedBy(currentUserId);
        }

        return toResponse(blogPostRepository.save(post));
    }

    @Transactional
    public BlogPostResponse updateStatus(UUID tenantId, UUID id, BlogPostStatus status) {
        BlogPost post = findOwned(tenantId, id);
        applyStatus(post, status, null);

        UUID currentUserId = TenantContext.getUserId();
        if (currentUserId != null) {
            post.setUpdatedBy(currentUserId);
        }

        return toResponse(blogPostRepository.save(post));
    }

    @Transactional
    public void delete(UUID tenantId, UUID id) {
        BlogPost post = findOwned(tenantId, id);
        post.setDeletedAt(LocalDateTime.now());
        blogPostRepository.save(post);
    }

    @Transactional(readOnly = true)
    public Page<PublicBlogPostSummaryResponse> listPublic(UUID tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt"));
        return blogPostRepository.findAllByTenantIdAndStatusAndDeletedAtIsNull(tenantId, BlogPostStatus.PUBLISHED, pageable)
                .map(this::toPublicSummary);
    }

    @Transactional(readOnly = true)
    public PublicBlogPostResponse getPublicBySlug(UUID tenantId, String slug) {
        BlogPost post = blogPostRepository.findByTenantIdAndSlugAndStatusAndDeletedAtIsNull(
                        tenantId,
                        slug,
                        BlogPostStatus.PUBLISHED
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog post not found"));
        return toPublic(post);
    }

    private BlogPost findOwned(UUID tenantId, UUID id) {
        return blogPostRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog post not found"));
    }

    private void applyStatus(BlogPost post, BlogPostStatus status, LocalDateTime publishedAtOverride) {
        if (status == post.getStatus()) return;
        post.setStatus(status);
        if (status == BlogPostStatus.PUBLISHED) {
            post.setPublishedAt(publishedAtOverride != null ? publishedAtOverride : LocalDateTime.now());
        } else {
            post.setPublishedAt(null);
        }
    }

    private String sanitizeContent(String contentHtml) {
        if (contentHtml == null || contentHtml.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contentHtml is required");
        }

        String clean = Jsoup.clean(contentHtml, CONTENT_SAFELIST).trim();
        String plain = Jsoup.parse(clean).text().trim();
        if (plain.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contentHtml is required");
        }
        return clean;
    }

    private String sanitizeExcerpt(String excerpt, String contentHtml) {
        String normalized = trimToNull(excerpt);
        if (normalized != null) {
            return normalized.length() > 400 ? normalized.substring(0, 400) : normalized;
        }

        String plain = Jsoup.parse(contentHtml).text().trim();
        if (plain.isBlank()) return null;
        return plain.length() > 280 ? plain.substring(0, 280) : plain;
    }

    private String ensureUniqueSlug(UUID tenantId, String requestedSlug, String fallbackTitle, UUID ignoreId) {
        String base = slugify(trimToNull(requestedSlug) != null ? requestedSlug : fallbackTitle);
        if (base.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid slug");
        }

        String candidate = base;
        int suffix = 2;
        while (ignoreId == null
                ? blogPostRepository.existsByTenantIdAndSlugAndDeletedAtIsNull(tenantId, candidate)
                : blogPostRepository.existsByTenantIdAndSlugAndDeletedAtIsNullAndIdNot(tenantId, candidate, ignoreId)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String slugify(String value) {
        if (value == null) return "";

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-");

        if (normalized.length() > 180) {
            normalized = normalized.substring(0, 180).replaceAll("-+$", "");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String getUserName(UUID userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(user -> {
                    String first = user.getFirstName() != null ? user.getFirstName() : "";
                    String last = user.getLastName() != null ? user.getLastName() : "";
                    return (first + " " + last).trim();
                })
                .filter(name -> !name.isBlank())
                .orElse(null);
    }

    private BlogPostResponse toResponse(BlogPost post) {
        return new BlogPostResponse(
                post.getId(),
                post.getTenantId(),
                post.getTitle(),
                post.getSlug(),
                post.getExcerpt(),
                post.getContentHtml(),
                post.getCoverImageUrl(),
                post.getStatus(),
                post.getPublishedAt(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getCreatedBy(),
                getUserName(post.getCreatedBy()),
                post.getUpdatedBy(),
                getUserName(post.getUpdatedBy())
        );
    }

    private PublicBlogPostSummaryResponse toPublicSummary(BlogPost post) {
        return new PublicBlogPostSummaryResponse(
                post.getId(),
                post.getTitle(),
                post.getSlug(),
                post.getExcerpt(),
                post.getCoverImageUrl(),
                post.getPublishedAt()
        );
    }

    private PublicBlogPostResponse toPublic(BlogPost post) {
        return new PublicBlogPostResponse(
                post.getId(),
                post.getTitle(),
                post.getSlug(),
                post.getExcerpt(),
                post.getContentHtml(),
                post.getCoverImageUrl(),
                post.getPublishedAt()
        );
    }
}
