package com.xzcpc.mp.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0: LoginUser 角色判定单元测试
 */
@DisplayName("LoginUser 角色判定")
class LoginUserTest {

    @Test
    @DisplayName("LoginUser.role 在登录时正确写入")
    void shouldStoreRole() {
        LoginUser user = new LoginUser(1L, "openid_001", "store_001", "测试门店", "张三", "store_manager");

        assertEquals("store_manager", user.getRole());
        assertTrue(user.isStoreManagerOrOwner());
        assertFalse(user.isStaffOnly());
    }

    @Test
    @DisplayName("owner 角色继承店长能力")
    void ownerShouldInheritStoreManagerCapability() {
        LoginUser user = new LoginUser(2L, "openid_002", "store_001", "测试门店", "李四", "owner");

        assertEquals("owner", user.getRole());
        assertTrue(user.isStoreManagerOrOwner());
        assertFalse(user.isStaffOnly());
    }

    @Test
    @DisplayName("staff 角色仅盘点执行权限")
    void staffShouldOnlyHaveInventoryPermission() {
        LoginUser user = new LoginUser(3L, "openid_003", "store_001", "测试门店", "王五", "staff");

        assertEquals("staff", user.getRole());
        assertTrue(user.isStaffOnly());
        assertFalse(user.isStoreManagerOrOwner());
    }

    @Test
    @DisplayName("无角色时默认为 null")
    void defaultRoleShouldBeNull() {
        LoginUser user = new LoginUser(4L, "openid_004", "store_001", "测试门店");

        assertNull(user.getRole());
        assertFalse(user.isStoreManagerOrOwner());
        assertFalse(user.isStaffOnly());
    }

    @Test
    @DisplayName("4 参数构造兼容旧代码（无 employeeName 和 role）")
    void fourArgConstructorShouldWork() {
        LoginUser user = new LoginUser(5L, "openid_005", "store_001", "测试门店");

        assertNotNull(user);
        assertNull(user.getEmployeeName());
        assertNull(user.getRole());
    }

    @Test
    @DisplayName("5 参数构造兼容旧代码（无 role）")
    void fiveArgConstructorShouldWork() {
        LoginUser user = new LoginUser(6L, "openid_006", "store_001", "测试门店", "赵六");

        assertNotNull(user);
        assertEquals("赵六", user.getEmployeeName());
        assertNull(user.getRole());
    }
}
