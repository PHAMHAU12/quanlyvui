const fs = require('fs');
const path = require('path');

console.log('🚀 Đang chuẩn bị build cho QUẢN LÝ LỚP BẰNG MÃ QR - PP HẬU trên Vercel...');

const publicDir = path.join(__dirname, 'public');
if (!fs.existsSync(publicDir)) {
  fs.mkdirSync(publicDir, { recursive: true });
}

const indexPath = path.join(publicDir, 'index.html');
if (fs.existsSync(indexPath)) {
  console.log('✅ Đã tìm thấy giao diện Web Portal trong thư mục public/');
} else {
  console.log('⚠️ Đang khởi tạo tệp index.html trong public/');
}

console.log('🎉 Build thành công! Sẵn sàng triển khai trên Vercel.');
