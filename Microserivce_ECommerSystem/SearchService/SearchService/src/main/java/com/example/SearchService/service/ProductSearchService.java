package com.example.SearchService.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.example.SearchService.document.ProductDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private static final int MAX_SIZE = 100;

    private final ElasticsearchOperations operations;

    /**
     * Tìm sản phẩm theo từ khóa, kết quả xếp theo độ liên quan (khớp tốt nhất đứng đầu).
     *
     * Dùng truy vấn "match" nên Elasticsearch tra thẳng từng từ trong chỉ mục đảo ngược,
     * thay cho kiểu "*từ khóa*" trước đây (phải duyệt qua mọi từ và không xếp hạng được).
     * Ba cách khớp, cách nào trúng cũng tính, trúng nhiều cách thì điểm cao hơn:
     *  1. Khớp đủ các từ trong tên (ưu tiên x3) hoặc mô tả.
     *  2. Từ cuối đang gõ dở: "ao th" vẫn ra "Áo thun".
     *  3. Gõ sai 1-2 ký tự ở từ dài: "iphnoe" vẫn ra "iPhone".
     */
    public List<ProductDocument> search(String keyword, int page, int size) {
        String text = keyword == null ? "" : keyword.trim();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE));

        NativeQuery query = text.isEmpty()
                ? NativeQuery.builder().withQuery(q -> q.matchAll(m -> m)).withPageable(pageable).build()
                : NativeQuery.builder()
                        .withQuery(q -> q.bool(b -> b
                                .should(s -> s.multiMatch(m -> m
                                        .query(text)
                                        .fields("name^3", "description")
                                        .operator(Operator.And)
                                        .boost(2.0f)))
                                .should(s -> s.multiMatch(m -> m
                                        .query(text)
                                        .fields("name^3", "description")
                                        .type(TextQueryType.BoolPrefix)
                                        .operator(Operator.And)))
                                .should(s -> s.multiMatch(m -> m
                                        .query(text)
                                        .fields("name^3", "description")
                                        .operator(Operator.And)
                                        // Từ 1-3 ký tự phải đúng hẳn, 4-7 ký tự cho sai 1, dài hơn cho sai 2.
                                        // Ký tự đầu phải đúng để tránh kết quả lạc đề.
                                        .fuzziness("AUTO:4,8")
                                        .prefixLength(1)
                                        .boost(0.5f)))
                                .minimumShouldMatch("1")))
                        .withPageable(pageable)
                        .build();

        return operations.search(query, ProductDocument.class)
                .stream()
                .map(SearchHit::getContent)
                .toList();
    }
}
