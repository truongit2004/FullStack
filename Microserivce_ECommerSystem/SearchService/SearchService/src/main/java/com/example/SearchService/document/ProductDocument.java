package com.example.SearchService.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;

/**
 * Bản sao của sản phẩm trong Elasticsearch, chỉ gồm các trường cần để tìm và hiển thị thẻ sản phẩm.
 * Tên trường trùng với JSON của ProductResponseDTO (Product Service) nên đọc thẳng được từ Kafka và từ API.
 * Tồn kho KHÔNG lưu ở đây vì thay đổi liên tục; trang chi tiết lấy tồn kho từ Product Service.
 *
 * Index tên "products_v2": bản "products" cũ dùng bộ phân tích khác, mà Elasticsearch không cho đổi
 * bộ phân tích của index đã tạo, nên dùng index mới (dữ liệu được nạp lại tự động từ Product Service).
 */
@Document(indexName = "#{@environment.getProperty('search.index-name', 'products_v2')}")
// Bộ phân tích "vi_text": tách từ, viết thường, bỏ dấu -> gõ "ao thun" vẫn ra "Áo thun"
@Setting(settingPath = "/elasticsearch/product-settings.json")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "vi_text")
    private String name;

    @Field(type = FieldType.Text, analyzer = "vi_text")
    private String description;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Double)
    private BigDecimal originalPrice;

    @Field(type = FieldType.Boolean)
    private Boolean onSale;

    @Field(type = FieldType.Integer)
    private Integer discountPercentage;

    @Field(type = FieldType.Keyword)
    private String categoryId;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Keyword)
    private String imageUrl;

    @Field(type = FieldType.Keyword)
    private String productStatus;
}
