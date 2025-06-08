-- 插入天文商店的初始库存数据
-- 这些产品ID应该与产品目录服务中的产品ID保持一致

INSERT INTO inventory (product_id, available_quantity, reserved_quantity, total_quantity, warehouse_location) VALUES
-- 望远镜类产品
('OLJCESPC7Z', 25, 0, 25, 'main-warehouse'), -- Vintage Typewriter
('66VCHSJNUP', 15, 0, 15, 'main-warehouse'), -- Vintage Camera Lens
('1YMWWN1N4O', 30, 0, 30, 'main-warehouse'), -- Home Barista Kit
('L9ECAV7KIM', 20, 0, 20, 'main-warehouse'), -- Terrarium
('2ZYFJ3GM2N', 40, 0, 40, 'main-warehouse'), -- Film Canister
('0PUK6V6EV0', 35, 0, 35, 'main-warehouse'), -- Vintage Record Player
('LS4PSXUNUM', 50, 0, 50, 'main-warehouse'), -- Metal Camping Mug
('9SIQT8TOJO', 45, 0, 45, 'main-warehouse'), -- City Bike
('6E92ZMYYFZ', 60, 0, 60, 'main-warehouse'), -- Air Plant

-- 天文观测设备
('TELESCOPE-001', 12, 0, 12, 'astronomy-warehouse'), -- Professional Telescope
('TELESCOPE-002', 8, 0, 8, 'astronomy-warehouse'), -- Beginner Telescope
('BINOCULARS-001', 25, 0, 25, 'astronomy-warehouse'), -- Astronomy Binoculars
('STAR-CHART-001', 100, 0, 100, 'main-warehouse'), -- Star Chart Set
('COMPASS-001', 30, 0, 30, 'main-warehouse'), -- Astronomical Compass

-- 户外装备
('CAMPING-TENT-001', 15, 0, 15, 'outdoor-warehouse'), -- 4-Person Camping Tent
('SLEEPING-BAG-001', 20, 0, 20, 'outdoor-warehouse'), -- All-Season Sleeping Bag
('BACKPACK-001', 18, 0, 18, 'outdoor-warehouse'), -- Hiking Backpack
('FLASHLIGHT-001', 40, 0, 40, 'main-warehouse'), -- LED Flashlight
('WATER-BOTTLE-001', 60, 0, 60, 'main-warehouse'), -- Insulated Water Bottle

-- 书籍和教育材料
('BOOK-ASTRONOMY-001', 80, 0, 80, 'main-warehouse'), -- Astronomy Guide Book
('BOOK-SPACE-001', 75, 0, 75, 'main-warehouse'), -- Space Exploration History
('POSTER-SOLAR-001', 50, 0, 50, 'main-warehouse'), -- Solar System Poster
('MODEL-ROCKET-001', 10, 0, 10, 'specialty-warehouse'), -- Model Rocket Kit

-- 服装配饰
('T-SHIRT-SPACE-001', 100, 0, 100, 'apparel-warehouse'), -- Space Theme T-Shirt
('HAT-ASTRONOMY-001', 45, 0, 45, 'apparel-warehouse'), -- Astronomy Club Hat
('JACKET-OUTDOOR-001', 25, 0, 25, 'apparel-warehouse'), -- Outdoor Jacket

-- 礼品和纪念品
('MUG-GALAXY-001', 80, 0, 80, 'main-warehouse'), -- Galaxy Print Mug
('KEYCHAIN-PLANET-001', 150, 0, 150, 'main-warehouse'), -- Planet Keychain Set
('STICKER-SPACE-001', 200, 0, 200, 'main-warehouse') -- Space Sticker Pack
ON CONFLICT (product_id) DO NOTHING;

-- 记录初始化操作
INSERT INTO inventory_operations (product_id, operation_type, quantity_change, reason)
SELECT product_id, 'initial_stock', total_quantity, 'Initial inventory setup for astronomy shop'
FROM inventory
WHERE product_id IN (
    'OLJCESPC7Z', '66VCHSJNUP', '1YMWWN1N4O', 'L9ECAV7KIM', '2ZYFJ3GM2N',
    '0PUK6V6EV0', 'LS4PSXUNUM', '9SIQT8TOJO', '6E92ZMYYFZ',
    'TELESCOPE-001', 'TELESCOPE-002', 'BINOCULARS-001', 'STAR-CHART-001', 'COMPASS-001',
    'CAMPING-TENT-001', 'SLEEPING-BAG-001', 'BACKPACK-001', 'FLASHLIGHT-001', 'WATER-BOTTLE-001',
    'BOOK-ASTRONOMY-001', 'BOOK-SPACE-001', 'POSTER-SOLAR-001', 'MODEL-ROCKET-001',
    'T-SHIRT-SPACE-001', 'HAT-ASTRONOMY-001', 'JACKET-OUTDOOR-001',
    'MUG-GALAXY-001', 'KEYCHAIN-PLANET-001', 'STICKER-SPACE-001'
); 