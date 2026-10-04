-- Expand the playable Vietnamese word bank and align historic words with the
-- new room category filters. All entries are drawable, family-friendly terms.

UPDATE words
SET category = 'TRANSPORT'
WHERE word IN ('xe đạp', 'ô tô', 'máy bay', 'tàu hỏa');

UPDATE words
SET category = 'SPORTS'
WHERE word = 'bóng đá';

UPDATE words
SET category = 'SCHOOL'
WHERE word IN ('cái cặp', 'bút chì');

UPDATE words
SET category = 'HOME'
WHERE word IN ('cái ghế', 'cái bàn');

INSERT INTO words (word, category) VALUES
    -- Động vật
    ('chim cánh cụt', 'ANIMALS'), ('hươu cao cổ', 'ANIMALS'),
    ('cá mập', 'ANIMALS'), ('sư tử', 'ANIMALS'), ('báo đốm', 'ANIMALS'),
    ('ngựa vằn', 'ANIMALS'), ('kangaroo', 'ANIMALS'), ('tê giác', 'ANIMALS'),
    ('hà mã', 'ANIMALS'), ('con sóc', 'ANIMALS'), ('con nhím', 'ANIMALS'),
    ('cú mèo', 'ANIMALS'), ('đại bàng', 'ANIMALS'), ('bướm', 'ANIMALS'),
    ('con ong', 'ANIMALS'), ('con kiến', 'ANIMALS'), ('bạch tuộc', 'ANIMALS'),
    ('cá ngựa', 'ANIMALS'), ('con cua', 'ANIMALS'), ('ốc sên', 'ANIMALS'),
    ('con ếch', 'ANIMALS'), ('con rắn', 'ANIMALS'), ('cá sấu', 'ANIMALS'),
    ('khủng long', 'ANIMALS'),

    -- Đồ ăn
    ('bánh xèo', 'FOOD'), ('bún bò', 'FOOD'), ('mì quảng', 'FOOD'),
    ('hủ tiếu', 'FOOD'), ('bánh cuốn', 'FOOD'), ('bánh bao', 'FOOD'),
    ('cháo', 'FOOD'), ('mì ý', 'FOOD'), ('hamburger', 'FOOD'), ('sushi', 'FOOD'),
    ('gà rán', 'FOOD'), ('khoai tây chiên', 'FOOD'), ('xúc xích', 'FOOD'),
    ('trứng ốp la', 'FOOD'), ('cá viên', 'FOOD'), ('bánh kem', 'FOOD'),
    ('bánh donut', 'FOOD'), ('sô cô la', 'FOOD'), ('nước cam', 'FOOD'),
    ('nước dừa', 'FOOD'), ('chè', 'FOOD'), ('bắp rang', 'FOOD'),
    ('xôi', 'FOOD'), ('bánh chưng', 'FOOD'),

    -- Đồ vật
    ('ba lô', 'OBJECTS'), ('chai nước', 'OBJECTS'), ('đèn pin', 'OBJECTS'),
    ('chìa khóa', 'OBJECTS'), ('kính mắt', 'OBJECTS'), ('cái lược', 'OBJECTS'),
    ('cái gương', 'OBJECTS'), ('nón bảo hiểm', 'OBJECTS'), ('bình hoa', 'OBJECTS'),
    ('cây chổi', 'OBJECTS'), ('cái quạt', 'OBJECTS'), ('ổ khóa', 'OBJECTS'),
    ('ví tiền', 'OBJECTS'), ('vali', 'OBJECTS'), ('hộp quà', 'OBJECTS'),
    ('cây nến', 'OBJECTS'), ('kính lúp', 'OBJECTS'), ('đèn giao thông', 'OBJECTS'),
    ('cây thước', 'OBJECTS'), ('máy sấy tóc', 'OBJECTS'), ('chiếc nhẫn', 'OBJECTS'),
    ('mũi tên', 'OBJECTS'), ('quả địa cầu', 'OBJECTS'),

    -- Địa điểm
    ('bưu điện', 'PLACES'), ('sở thú', 'PLACES'), ('rạp chiếu phim', 'PLACES'),
    ('bến xe', 'PLACES'), ('bến tàu', 'PLACES'), ('nhà ga', 'PLACES'),
    ('hồ bơi', 'PLACES'), ('sân vận động', 'PLACES'), ('tiệm bánh', 'PLACES'),
    ('quán cà phê', 'PLACES'), ('bảo tàng', 'PLACES'), ('chợ', 'PLACES'),
    ('nông trại', 'PLACES'), ('khu vui chơi', 'PLACES'), ('bãi biển', 'PLACES'),
    ('hòn đảo', 'PLACES'), ('lâu đài', 'PLACES'), ('nhà thờ', 'PLACES'),
    ('đồn cảnh sát', 'PLACES'), ('trạm cứu hỏa', 'PLACES'),

    -- Thiên nhiên
    ('rừng cây', 'NATURE'), ('thác nước', 'NATURE'), ('sa mạc', 'NATURE'),
    ('núi lửa', 'NATURE'), ('hang động', 'NATURE'), ('hồ nước', 'NATURE'),
    ('đồng cỏ', 'NATURE'), ('tuyết', 'NATURE'), ('sấm sét', 'NATURE'),
    ('cơn bão', 'NATURE'), ('lá cây', 'NATURE'), ('hạt mưa', 'NATURE'),
    ('mặt trăng', 'NATURE'), ('sao băng', 'NATURE'), ('bình minh', 'NATURE'),
    ('hoàng hôn', 'NATURE'), ('cánh đồng', 'NATURE'), ('san hô', 'NATURE'),
    ('cây xương rồng', 'NATURE'), ('tổ chim', 'NATURE'),

    -- Công nghệ
    ('điện thoại thông minh', 'TECHNOLOGY'), ('bàn phím', 'TECHNOLOGY'),
    ('chuột máy tính', 'TECHNOLOGY'), ('loa', 'TECHNOLOGY'), ('micro', 'TECHNOLOGY'),
    ('máy in', 'TECHNOLOGY'), ('máy quét', 'TECHNOLOGY'), ('ổ cứng', 'TECHNOLOGY'),
    ('usb', 'TECHNOLOGY'), ('đồng hồ thông minh', 'TECHNOLOGY'),
    ('kính thực tế ảo', 'TECHNOLOGY'), ('máy bay không người lái', 'TECHNOLOGY'),
    ('trí tuệ nhân tạo', 'TECHNOLOGY'), ('mã vạch', 'TECHNOLOGY'),
    ('sạc dự phòng', 'TECHNOLOGY'), ('tai nghe không dây', 'TECHNOLOGY'),
    ('màn hình', 'TECHNOLOGY'), ('máy chủ', 'TECHNOLOGY'),
    ('điều khiển từ xa', 'TECHNOLOGY'), ('ứng dụng', 'TECHNOLOGY'),

    -- Giao thông
    ('xe máy', 'TRANSPORT'), ('xe buýt', 'TRANSPORT'), ('xe tải', 'TRANSPORT'),
    ('xe cứu hỏa', 'TRANSPORT'), ('xe cứu thương', 'TRANSPORT'), ('taxi', 'TRANSPORT'),
    ('thuyền buồm', 'TRANSPORT'), ('ca nô', 'TRANSPORT'), ('tàu ngầm', 'TRANSPORT'),
    ('tàu vũ trụ', 'TRANSPORT'), ('khinh khí cầu', 'TRANSPORT'),
    ('xe trượt tuyết', 'TRANSPORT'), ('ván trượt', 'TRANSPORT'),
    ('xe điện', 'TRANSPORT'), ('xe cáp treo', 'TRANSPORT'), ('xe kéo', 'TRANSPORT'),
    ('trực thăng', 'TRANSPORT'), ('xe cảnh sát', 'TRANSPORT'), ('xe rác', 'TRANSPORT'),
    ('xe đẩy', 'TRANSPORT'), ('xe đua', 'TRANSPORT'), ('xe nâng', 'TRANSPORT'),

    -- Thể thao
    ('bóng rổ', 'SPORTS'), ('bóng chuyền', 'SPORTS'), ('cầu lông', 'SPORTS'),
    ('bơi lội', 'SPORTS'), ('chạy bộ', 'SPORTS'), ('nhảy cao', 'SPORTS'),
    ('bóng bàn', 'SPORTS'), ('quần vợt', 'SPORTS'), ('võ thuật', 'SPORTS'),
    ('boxing', 'SPORTS'), ('đua xe', 'SPORTS'), ('trượt băng', 'SPORTS'),
    ('trượt ván', 'SPORTS'), ('leo núi', 'SPORTS'), ('cờ vua', 'SPORTS'),
    ('kéo co', 'SPORTS'), ('bắn cung', 'SPORTS'), ('lướt sóng', 'SPORTS'),
    ('thể dục dụng cụ', 'SPORTS'), ('đá cầu', 'SPORTS'), ('nhảy dây', 'SPORTS'),
    ('bowling', 'SPORTS'),

    -- Nghề nghiệp
    ('bác sĩ', 'PROFESSIONS'), ('y tá', 'PROFESSIONS'), ('giáo viên', 'PROFESSIONS'),
    ('đầu bếp', 'PROFESSIONS'), ('lính cứu hỏa', 'PROFESSIONS'),
    ('cảnh sát', 'PROFESSIONS'), ('phi công', 'PROFESSIONS'), ('tài xế', 'PROFESSIONS'),
    ('thợ xây', 'PROFESSIONS'), ('kiến trúc sư', 'PROFESSIONS'), ('ca sĩ', 'PROFESSIONS'),
    ('diễn viên', 'PROFESSIONS'), ('họa sĩ', 'PROFESSIONS'),
    ('nhiếp ảnh gia', 'PROFESSIONS'), ('nhà khoa học', 'PROFESSIONS'),
    ('lập trình viên', 'PROFESSIONS'), ('nông dân', 'PROFESSIONS'),
    ('ngư dân', 'PROFESSIONS'), ('thợ cắt tóc', 'PROFESSIONS'),
    ('nhân viên bán hàng', 'PROFESSIONS'), ('nhà báo', 'PROFESSIONS'),
    ('thợ điện', 'PROFESSIONS'),

    -- Học đường
    ('bảng đen', 'SCHOOL'), ('sách giáo khoa', 'SCHOOL'), ('vở ghi', 'SCHOOL'),
    ('bút mực', 'SCHOOL'), ('cục tẩy', 'SCHOOL'), ('thước kẻ', 'SCHOOL'),
    ('compa', 'SCHOOL'), ('máy tính cầm tay', 'SCHOOL'), ('cặp sách', 'SCHOOL'),
    ('hộp bút', 'SCHOOL'), ('xe đưa đón', 'SCHOOL'), ('sân trường', 'SCHOOL'),
    ('đồng phục', 'SCHOOL'), ('bài kiểm tra', 'SCHOOL'), ('thí nghiệm', 'SCHOOL'),
    ('bản đồ', 'SCHOOL'), ('bút màu', 'SCHOOL'), ('giấy thủ công', 'SCHOOL'),
    ('keo dán', 'SCHOOL'), ('kéo thủ công', 'SCHOOL'), ('huy chương', 'SCHOOL'),

    -- Nhà cửa
    ('giường', 'HOME'), ('cái gối', 'HOME'), ('chăn', 'HOME'), ('tủ lạnh', 'HOME'),
    ('bếp ga', 'HOME'), ('lò vi sóng', 'HOME'), ('máy giặt', 'HOME'),
    ('bồn rửa', 'HOME'), ('bồn tắm', 'HOME'), ('vòi hoa sen', 'HOME'),
    ('bàn ăn', 'HOME'), ('ghế sofa', 'HOME'), ('thảm', 'HOME'), ('rèm cửa', 'HOME'),
    ('cửa sổ', 'HOME'), ('cầu thang', 'HOME'), ('điều hòa', 'HOME'),
    ('tủ quần áo', 'HOME'), ('khung ảnh', 'HOME'), ('chuông cửa', 'HOME'),
    ('nồi cơm điện', 'HOME'), ('ly nước', 'HOME'), ('đĩa ăn', 'HOME'), ('ấm nước', 'HOME'),

    -- Giải trí
    ('đàn ghi ta', 'ENTERTAINMENT'), ('đàn piano', 'ENTERTAINMENT'),
    ('trống', 'ENTERTAINMENT'), ('sáo', 'ENTERTAINMENT'), ('sân khấu', 'ENTERTAINMENT'),
    ('vé xem phim', 'ENTERTAINMENT'), ('rạp xiếc', 'ENTERTAINMENT'),
    ('truyện tranh', 'ENTERTAINMENT'), ('bảng vẽ', 'ENTERTAINMENT'),
    ('con rối', 'ENTERTAINMENT'), ('bóng bay', 'ENTERTAINMENT'),
    ('pháo hoa', 'ENTERTAINMENT'), ('ảo thuật', 'ENTERTAINMENT'),
    ('máy karaoke', 'ENTERTAINMENT'), ('đĩa nhạc', 'ENTERTAINMENT'),
    ('xếp hình', 'ENTERTAINMENT'), ('búp bê', 'ENTERTAINMENT'),
    ('xe đồ chơi', 'ENTERTAINMENT'), ('gấu bông', 'ENTERTAINMENT'),
    ('lều cắm trại', 'ENTERTAINMENT'), ('vòng quay', 'ENTERTAINMENT'),
    ('tàu lượn', 'ENTERTAINMENT'), ('con diều', 'ENTERTAINMENT'),
    ('bong bóng xà phòng', 'ENTERTAINMENT'),

    -- Trang phục
    ('áo thun', 'CLOTHING'), ('áo sơ mi', 'CLOTHING'), ('áo khoác', 'CLOTHING'),
    ('váy', 'CLOTHING'), ('quần jean', 'CLOTHING'), ('giày thể thao', 'CLOTHING'),
    ('dép', 'CLOTHING'), ('mũ lưỡi trai', 'CLOTHING'), ('khăn quàng', 'CLOTHING'),
    ('găng tay', 'CLOTHING'), ('tất', 'CLOTHING'), ('cà vạt', 'CLOTHING'),
    ('kính râm', 'CLOTHING'), ('túi xách', 'CLOTHING'),
    ('đồng hồ đeo tay', 'CLOTHING'), ('áo mưa', 'CLOTHING'),
    ('mũ len', 'CLOTHING'), ('ủng', 'CLOTHING'), ('vòng cổ', 'CLOTHING'),

    -- Việt Nam
    ('áo dài', 'VIETNAM'), ('nón lá', 'VIETNAM'), ('trống đồng', 'VIETNAM'),
    ('vịnh Hạ Long', 'VIETNAM'), ('phố cổ Hội An', 'VIETNAM'),
    ('chùa Một Cột', 'VIETNAM'), ('cầu Rồng', 'VIETNAM'),
    ('ruộng bậc thang', 'VIETNAM'), ('hoa sen', 'VIETNAM'),
    ('cà phê sữa đá', 'VIETNAM'), ('bánh tét', 'VIETNAM'), ('gỏi cuốn', 'VIETNAM'),
    ('đèn lồng', 'VIETNAM'), ('múa lân', 'VIETNAM'),
    ('Tết Nguyên Đán', 'VIETNAM'), ('bánh tráng', 'VIETNAM'),
    ('xe xích lô', 'VIETNAM'), ('chợ Bến Thành', 'VIETNAM'),
    ('núi Fansipan', 'VIETNAM'), ('sông Hương', 'VIETNAM'),
    ('tháp Rùa', 'VIETNAM'), ('lúa nước', 'VIETNAM')
ON CONFLICT (word) DO UPDATE SET category = EXCLUDED.category;
