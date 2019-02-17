-- MySQL Workbench Forward Engineering

SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='TRADITIONAL,ALLOW_INVALID_DATES';

-- -----------------------------------------------------
-- Schema mydb
-- -----------------------------------------------------
-- -----------------------------------------------------
-- Schema SUPERSTARS
-- -----------------------------------------------------

-- -----------------------------------------------------
-- Schema SUPERSTARS
-- -----------------------------------------------------
CREATE SCHEMA IF NOT EXISTS `SUPERSTARS` DEFAULT CHARACTER SET utf8 ;
USE `SUPERSTARS` ;

-- -----------------------------------------------------
-- Table `SUPERSTARS`.`Devices`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `SUPERSTARS`.`Devices` (
  `id` INT(10) UNSIGNED NOT NULL AUTO_INCREMENT,
  `android_id` CHAR(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `android_id_UNIQUE` (`android_id` ASC))
ENGINE = InnoDB
AUTO_INCREMENT = 301
DEFAULT CHARACTER SET = utf8;


-- -----------------------------------------------------
-- Table `SUPERSTARS`.`Items`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `SUPERSTARS`.`Items` (
  `id` INT(10) UNSIGNED NOT NULL AUTO_INCREMENT,
  `parent_item` INT(10) UNSIGNED NULL DEFAULT NULL,
  `hash_name` CHAR(20) NULL DEFAULT NULL,
  `creator_device` INT(10) UNSIGNED NOT NULL,
  `review` VARCHAR(1000) NOT NULL DEFAULT '',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `rating` TINYINT(4) NOT NULL DEFAULT '0',
  `is_visible` TINYINT(4) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `hash_name_UNIQUE` (`hash_name` ASC, `creator_device` ASC),
  INDEX `fk_Items_Devices1_idx` (`creator_device` ASC),
  INDEX `fk_Items_1_idx` (`parent_item` ASC),
  CONSTRAINT `fk_Items_1`
    FOREIGN KEY (`parent_item`)
    REFERENCES `SUPERSTARS`.`Items` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_Items_Devices1`
    FOREIGN KEY (`creator_device`)
    REFERENCES `SUPERSTARS`.`Devices` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
AUTO_INCREMENT = 252
DEFAULT CHARACTER SET = utf8;


-- -----------------------------------------------------
-- Table `SUPERSTARS`.`Tags`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `SUPERSTARS`.`Tags` (
  `item` INT(10) UNSIGNED NOT NULL,
  `tag` VARCHAR(45) NOT NULL,
  PRIMARY KEY (`item`, `tag`),
  INDEX `fk_Tags_Items1_idx` (`item` ASC),
  CONSTRAINT `fk_Tags_Items1`
    FOREIGN KEY (`item`)
    REFERENCES `SUPERSTARS`.`Items` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8;

USE `SUPERSTARS` ;

-- -----------------------------------------------------
-- procedure add_device
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` PROCEDURE `add_device`(
	IN android_id CHAR(16)
)
BEGIN
	INSERT INTO Devices (`android_id`) VALUES (android_id);
	#SET device_id = LAST_INSERT_ID();
END$$

DELIMITER ;

-- -----------------------------------------------------
-- procedure add_item
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` PROCEDURE `add_item`(
	IN item_hash CHAR(20),
	IN device INT UNSIGNED,
    IN parent INT UNSIGNED,
    IN create_date TIMESTAMP
)
BEGIN
	-- DECLARE h_name 			CHAR(20);
    -- DECLARE next_id 		INT UNSIGNED;    

	-- получаем следующий id
	-- SELECT `AUTO_INCREMENT` INTO next_id
	-- FROM  INFORMATION_SCHEMA.TABLES
	-- WHERE TABLE_SCHEMA = DATABASE()
	-- AND   TABLE_NAME   = 'Items';

	-- SET h_name = generate_hash_name(next_id, device);
		
	INSERT INTO Items (`hash_name`, `creator_device`, `parent_item`, `create_date`) 
    VALUES (item_hash, device, parent, create_date);
END$$

DELIMITER ;

-- -----------------------------------------------------
-- procedure add_item_by_hashes
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` PROCEDURE `add_item_by_hashes`(
	IN item_hash CHAR(20),
    IN android_hash CHAR(16),
    IN parent_hash CHAR(20),
    IN create_date TIMESTAMP
)
BEGIN
	DECLARE parent_id INT UNSIGNED;
    DECLARE device_id INT UNSIGNED;
    
    IF parent_hash IS NOT NULL THEN
		SELECT `id` INTO parent_id
			FROM `Items`
			WHERE `hash_name` = parent_hash LIMIT 1;
	END IF;
    
    SELECT `id` INTO device_id
		FROM `Devices`
        WHERE `android_id` = android_hash LIMIT 1;
        
	call add_item(item_hash, device_id, parent_id, create_date);
END$$

DELIMITER ;

-- -----------------------------------------------------
-- procedure add_mirror
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` PROCEDURE `add_mirror`(
	IN device_id 	INT UNSIGNED,
    IN parent_hash 	CHAR(20)
)
BEGIN
	DECLARE parent_device 	INT UNSIGNED;
    DECLARE ancestor 		INT UNSIGNED;
    DECLARE next_id 		INT UNSIGNED;
    DECLARE parent_id		INT UNSIGNED;
    
	IF NOT is_item_exists(device_id, parent_hash) THEN
			DROP TABLE IF EXISTS __tmp_add_item;
			CREATE TEMPORARY TABLE __tmp_add_item 
				AS SELECT * FROM Items WHERE `hash_name` <=> parent_hash LIMIT 1;
		
			SELECT `creator_device` INTO parent_device FROM __tmp_add_item;
			SELECT `parent_item` INTO ancestor FROM __tmp_add_item;
			SELECT `id` INTO parent_id FROM __tmp_add_item;
            
			IF device_id = parent_device THEN
				SET @msg =  CONCAT('Recursion isn`t allowed! ', CONVERT(parent_device,char));
				SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @msg;
			END IF;
			
			IF ancestor IS NOT NULL THEN
				SET parent_id = ancestor;
			END IF;
            
            INSERT INTO Items (`hash_name`, `creator_device`, `parent_item`) VALUES (NULL, device_id, parent_id);
	ELSE
		SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Item already exists';
	END IF;
END$$

DELIMITER ;

-- -----------------------------------------------------
-- procedure add_tag
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` PROCEDURE `add_tag`(
	IN item_id INT UNSIGNED,
	IN tag VARCHAR(45)
)
BEGIN
	INSERT INTO Tags (`item`, `tag`) VALUES (item_id, tag);
END$$

DELIMITER ;

-- -----------------------------------------------------
-- procedure cleanUp
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` PROCEDURE `cleanUp`()
BEGIN
	SET FOREIGN_KEY_CHECKS=0;
    
    TRUNCATE TABLE Devices;
    TRUNCATE TABLE Items;
    TRUNCATE TABLE Tags;
    
    SET FOREIGN_KEY_CHECKS=1;
END$$

DELIMITER ;

-- -----------------------------------------------------
-- procedure del_item
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` PROCEDURE `del_item`(
	IN item_id INT UNSIGNED
)
BEGIN
	DECLARE children_count INT;
    SET children_count = (select count(`id`) from Items where `parent_item` <=> item_id);

	-- если нет наследников, то можно смело удалять строку
    if children_count = 0 THEN
		CALL del_item_tags(item_id);
		DELETE FROM Items WHERE `id` = item_id LIMIT 1;
    ELSE
		UPDATE Items SET `is_visible` = 0 WHERE `id` = item_id LIMIT 1;
    END IF;
END$$

DELIMITER ;

-- -----------------------------------------------------
-- procedure del_item_tags
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` PROCEDURE `del_item_tags`(
	IN item_id INT UNSIGNED
)
BEGIN
	DELETE FROM Tags where `item` = item_id;
END$$

DELIMITER ;

-- -----------------------------------------------------
-- function find_item
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` FUNCTION `find_item`(
	h_name CHAR(20),
	android CHAR(16)
) RETURNS int(10) unsigned
BEGIN
	DECLARE found_item INT UNSIGNED;
    
    SELECT `item_id` INTO found_item
    FROM (
		SELECT `Items`.`id` as `item_id`,
			   `Devices`.`android_id` as `android_id`
		FROM( `Items` JOIN `Devices` ON `Items`.`creator_device` = `Devices`.`id`)
    ) AS tmp
	WHERE get_item_hash(`item_id`) = h_name AND `android_id` = android;

RETURN found_item;
END$$

DELIMITER ;

-- -----------------------------------------------------
-- function generate_hash_name
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` FUNCTION `generate_hash_name`(
	item_id 	INT UNSIGNED,
	device_id 	INT UNSIGNED
) RETURNS char(20) CHARSET utf8
BEGIN
	DECLARE generated_hash 	CHAR(20);
	DECLARE android_uuid 	CHAR(16);
        
	IF device_id IS NOT NULL THEN
		SELECT `android_id` INTO android_uuid FROM Devices
			WHERE `id` = device_id;
        
        -- генерируем хеш и удаляем 1й символ *
        SET generated_hash = SUBSTRING(PASSWORD(CONCAT(NOW(), android_uuid, CONVERT(item_id, CHAR))), 2, 20);
	END IF;
    
	RETURN generated_hash;
END$$

DELIMITER ;

-- -----------------------------------------------------
-- function get_item_hash
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` FUNCTION `get_item_hash`(
	item_id INT UNSIGNED
) RETURNS char(20) CHARSET utf8
BEGIN
	DECLARE parent INT UNSIGNED;
	DECLARE h_name CHAR(20);
    
    SELECT `parent_item` INTO parent FROM Items 
		WHERE `id` = item_id LIMIT 1;
    
    IF parent IS NULL THEN
		SELECT `hash_name` INTO h_name FROM Items 
			WHERE `id` = item_id LIMIT 1;
	ELSE
		SELECT `hash_name` INTO h_name FROM Items 
			WHERE `id` = parent LIMIT 1;
    END IF;
    
    -- NULL when item is not found
	RETURN h_name;
END$$

DELIMITER ;

-- -----------------------------------------------------
-- function is_item_exists
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` FUNCTION `is_item_exists`(
	device_id INT UNSIGNED,
    item_hash CHAR(20)
) RETURNS tinyint(4)
BEGIN
	DECLARE found_id INT UNSIGNED;
    
    SELECT `id` INTO found_id
		FROM `Items`
        WHERE get_item_hash(`id`) = item_hash AND `creator_device` = device_id LIMIT 1;
        
	RETURN found_id IS NOT NULL;
END$$

DELIMITER ;

-- -----------------------------------------------------
-- procedure saved
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` PROCEDURE `saved`(
	IN device		INT UNSIGNED,
	IN parent		INT UNSIGNED
)
BEGIN
	DECLARE h_name 			VARCHAR(41);
	DECLARE parent_device 	INT UNSIGNED;
    DECLARE ancestor 		INT UNSIGNED;
    DECLARE next_id 		INT UNSIGNED;
    
    IF NOT is_item_exists(device, get_item_hash(parent)) THEN
		IF parent IS NOT NULL THEN
			DROP TABLE IF EXISTS __tmp_add_item;
			CREATE TEMPORARY TABLE __tmp_add_item 
				AS SELECT * FROM Items WHERE `id` = parent LIMIT 1;
		
			SELECT `creator_device` INTO parent_device FROM __tmp_add_item;
			SELECT `parent_item` INTO ancestor FROM __tmp_add_item;
			
			-- имя таблицы можно восстановить по родителю
			SET h_name = NULL;
			
			IF device = parent_device THEN
				SET @msg =  CONCAT('Recursion isn`t allowed! ',CONVERT(parent_device,char));
				SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @msg;
			END IF;
			
			IF ancestor IS NOT NULL THEN
				SET parent = ancestor;
			END IF;
		ELSE
			-- получаем следующий id
			SELECT `AUTO_INCREMENT` INTO next_id
			FROM  INFORMATION_SCHEMA.TABLES
			WHERE TABLE_SCHEMA = DATABASE()
			AND   TABLE_NAME   = 'Items';
		
			SET h_name = generate_hash_name(next_id, device);
		END IF;
		
		INSERT INTO Items (`hash_name`, `creator_device`, `parent_item`) VALUES (h_name, device, parent);
    END IF;
END$$

DELIMITER ;

-- -----------------------------------------------------
-- procedure select_all_items
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` PROCEDURE `select_all_items`(IN viewPage INT)
BEGIN

	SELECT tmp.id, hash_name, parent_item, review, rating, last_update, create_date, android_id  FROM (
		(SELECT `Items`.id, parent_item, get_item_hash(id) as hash_name, creator_device, review, rating, last_update, create_date, is_visible FROM Items) as tmp
		JOIN  Devices 
		ON tmp.creator_device = Devices.id AND tmp.is_visible > 0
	);

END$$

DELIMITER ;

-- -----------------------------------------------------
-- procedure select_item_by_hash
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` PROCEDURE `select_item_by_hash`(
	IN item_hash CHAR(20)
)
BEGIN    
	SELECT tmp.id, hash_name, parent_item, review, rating, UNIX_TIMESTAMP(last_update) as last_update, UNIX_TIMESTAMP(create_date) as create_date, android_id  FROM (
		(SELECT `Items`.id, parent_item, hash_name, creator_device, review, rating, last_update, create_date, is_visible FROM Items) as tmp
		JOIN  Devices 
		ON tmp.creator_device = Devices.id AND tmp.is_visible > 0
	)
	WHERE hash_name <=> item_hash LIMIT 1;
END$$

DELIMITER ;

-- -----------------------------------------------------
-- procedure select_item_subscribers
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` PROCEDURE `select_item_subscribers`(
	IN item_id INT UNSIGNED
)
BEGIN      
    IF item_id IS NOT NULL THEN
        SELECT  itemId as id, get_item_hash(itemId) as hash_name, parent_item, review, rating, UNIX_TIMESTAMP(last_update) as last_update, UNIX_TIMESTAMP(create_date) as create_date, android_id
        FROM 
			(SELECT `Items`.id as itemId, hash_name, review, rating, last_update, create_date, parent_item, android_id
            FROM (`Items` JOIN `Devices` ON `Items`.creator_device = `Devices`.id)) as tmp1
        WHERE tmp1.parent_item = item_id OR tmp1.itemId = item_id
        ORDER BY -`parent_item` DESC;
    END IF;
END$$

DELIMITER ;

-- -----------------------------------------------------
-- procedure select_items_by_device
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` PROCEDURE `select_items_by_device`(
	IN device CHAR(16)
)
BEGIN
	SELECT tmp.id, hash_name, parent_item, review, rating, last_update, create_date, android_id  FROM (
		(SELECT `Items`.id, parent_item, get_item_hash(id) as hash_name, creator_device, review, rating, last_update, create_date, is_visible FROM Items) as tmp
		JOIN  Devices 
		ON tmp.creator_device = Devices.id AND tmp.is_visible > 0
	)
	WHERE android_id = device;
END$$

DELIMITER ;

-- -----------------------------------------------------
-- procedure select_mirrors_by_device
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` PROCEDURE `select_mirrors_by_device`(
	IN device CHAR(16)
)
BEGIN
	SELECT get_item_hash(Items.id) as hash_name, rating, review, last_update, create_date FROM (
		Items
		JOIN(
				SELECT parent_item
				FROM
				(
					(SELECT * FROM Items WHERE parent_item IS NOT NULL) as tmp 
					JOIN 
					Devices 
					ON tmp.creator_device = Devices.id AND Devices.android_id = device
				)
			) as tmp2
		ON Items.id = tmp2.parent_item
	)
	WHERE Items.is_visible > 0;
END$$

DELIMITER ;

-- -----------------------------------------------------
-- procedure update_review
-- -----------------------------------------------------

DELIMITER $$
USE `SUPERSTARS`$$
CREATE DEFINER=`superstars`@`%` PROCEDURE `update_review`(
	IN item_id INT UNSIGNED,
    IN review VARCHAR(1000),
    IN rating TINYINT
)
BEGIN
	UPDATE Items SET `review` = review, `rating` = rating WHERE `id` = item_id;
END$$

DELIMITER ;

SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;
