$outputPath = "d:\Downloads\FullStack\Microserivce_ECommerSystem\ProductService\ProductService\insert_10000_products.sql"
Write-Output "Bắt đầu tạo file SQL..."
$file = [System.IO.StreamWriter]::new($outputPath, $false, [System.Text.Encoding]::UTF8)

$file.WriteLine("INSERT INTO product (id, name, description, price, category_id, product_status, deleted, created_at, updated_at, average_rating, review_count, is_on_sale) VALUES ")

for ($i=1; $i -le 10000; $i++) {
    $uuid = [guid]::NewGuid().ToString()
    $name = "Sản phẩm MySQL $i"
    $desc = "Đây là dòng mô tả tĩnh để test việc indexing vào ES và querying tốc độ cao cho mặt hàng $i"
    $price = $i * 1000
    
    $line = "('$uuid', '$name', '$desc', $price, 'ae37e974-5396-4142-970f-4a6a5d399b3f', 'ACTIVE', 0, NOW(), NOW(), 0, 0, 0)"
    
    if ($i -eq 10000) {
        $file.WriteLine($line + ";")
    } else {
        $file.WriteLine($line + ",")
    }
}

$file.Close()
Write-Output "Tạo file thành công tại: $outputPath"
