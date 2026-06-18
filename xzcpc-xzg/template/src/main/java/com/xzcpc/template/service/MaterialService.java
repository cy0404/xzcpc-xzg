package com.xzcpc.template.service;

import com.xzcpc.common.model.MaterialInfo;
import com.xzcpc.template.entity.Material;

import java.util.List;
import java.util.Map;

public interface MaterialService {

    /** 一次性迁移：从外部 API 拉取盘点单位，写入 material_inventory_rule */
    int migrateInventoryUnits();

    /** 手动触发从外部 API 全量同步物料 */
    int syncFromApi();

    /** 以 MaterialInfo 返回全部物料（存量调用方兼容） */
    List<MaterialInfo> getAllMaterials();

    /** 以 MaterialInfo 模糊搜索物料 */
    List<MaterialInfo> searchMaterials(String keyword);

    /** 分页查询，返回 { records, total } 格式 */
    Map<String, Object> page(String keyword, int pageNum, int pageSize);

    /** 按业务 materialId 查询 */
    Material getByMaterialId(String materialId);

    /** 按业务 materialId 直接查库获取 MaterialInfo（不走缓存），查不到返回 null */
    MaterialInfo getMaterialInfoById(String materialId);

    /** 新增物料 */
    Material create(Material material);

    /** 更新物料（按业务 materialId） */
    Material update(String materialId, Material material);

    /** 删除物料（逻辑删除），被模板引用则拒绝 */
    void deleteByMaterialId(String materialId);

    /** 启停用物料 */
    void toggleStatus(String materialId);

    /** 查询全部物料分类（去重） */
    List<String> getAllCategories();

    /** 查询全部父级分类（去重） */
    List<String> getAllParentCategories();
}
