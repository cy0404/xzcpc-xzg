<template>
  <div class="people-page">
    <PeopleModuleTabs />

    <div class="detail-header">
      <a-button type="text" class="back-btn" @click="$router.push('/people')">
        <ArrowLeftOutlined />
      </a-button>
      <div class="detail-title-block">
        <h1 class="page-title">{{ employee.name }}</h1>
        <p class="page-subtitle">{{ employee.storeName }} / {{ employee.role }} / {{ employee.status }}</p>
      </div>
      <a-tag color="green">只读档案</a-tag>
    </div>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="14">
        <a-card title="基本信息" :bordered="false" class="section-card">
          <div class="info-grid">
            <div v-for="item in basicInfo" :key="item.label" class="info-item">
              <div class="info-label">{{ item.label }}</div>
              <div class="info-value">{{ item.value }}</div>
            </div>
          </div>
        </a-card>

        <a-card title="当前工具权限" :bordered="false" class="section-card">
          <div class="permission-grid">
            <div v-for="item in permissions" :key="item.name" class="permission-item">
              <a-tag :color="item.enabled ? 'green' : 'default'">{{ item.enabled ? '已开通' : '未开通' }}</a-tag>
              <strong>{{ item.name }}</strong>
            </div>
          </div>
        </a-card>
      </a-col>

      <a-col :xs="24" :lg="10">
        <a-card title="入职/离职时间线" :bordered="false" class="section-card">
          <a-timeline>
            <a-timeline-item v-for="item in timeline" :key="item.date">
              <div class="timeline-date">{{ item.date }}</div>
              <strong>{{ item.title }}</strong>
              <div class="timeline-desc">{{ item.desc }}</div>
            </a-timeline-item>
          </a-timeline>
        </a-card>

        <a-card title="历史记录" :bordered="false" class="section-card">
          <div class="history-list">
            <div v-for="item in histories" :key="item.label" class="history-item">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <em>{{ item.desc }}</em>
            </div>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import PeopleModuleTabs from '../../components/PeopleModuleTabs.vue'
import { getEmployeeDetail } from '../../api/people'

type EmployeeDetail = {
  id: number
  employeeId: string
  name: string
  mobile: string
  gender: string
  birthday: string
  storeName: string
  role: string
  employmentType: string
  entryDate: string
  status: string
}

const route = useRoute()
const employee = ref<EmployeeDetail>({
  id: 1,
  employeeId: 'EMP00000001',
  name: '王小丽',
  mobile: '138****5621',
  gender: '女',
  birthday: '1994-06-12',
  storeName: '南山万象店',
  role: '店长',
  employmentType: '全职',
  entryDate: '2024-08-12',
  status: '在职',
})

const permissions = ref([
  { name: '门店盘点', enabled: true },
  { name: '支出登记', enabled: true },
  { name: '员工管理', enabled: true },
  { name: '总部后台', enabled: false },
])

const timeline = ref([
  { date: '2024-08-10', title: '提交员工登记', desc: '由门店端完成信息登记' },
  { date: '2024-08-11', title: '审批通过', desc: '店长权限生效' },
  { date: '2024-08-12', title: '正式入职', desc: '绑定南山万象店' },
])

const histories = ref([
  { label: '盘点记录', value: '18 次', desc: '最近提交：2026-04-28' },
  { label: '支出记录', value: '24 笔', desc: '最近登记：2026-04-27' },
])

const basicInfo = computed(() => [
  { label: '姓名', value: employee.value.name },
  { label: '员工编号', value: employee.value.employeeId },
  { label: '手机号', value: employee.value.mobile },
  { label: '性别', value: employee.value.gender },
  { label: '出生日期', value: employee.value.birthday },
  { label: '所属门店', value: employee.value.storeName },
  { label: '岗位', value: employee.value.role },
  { label: '用工类型', value: employee.value.employmentType },
  { label: '入职日期', value: employee.value.entryDate },
  { label: '当前状态', value: employee.value.status },
])

function normalizeDetail(raw: any): EmployeeDetail {
  return {
    id: raw.id,
    employeeId: raw.employeeId || raw.employee_id || String(raw.id),
    name: raw.name,
    mobile: raw.mobile || raw.phone || '未填写',
    gender: raw.gender || '未填写',
    birthday: raw.birthday || raw.birthDate || '未填写',
    storeName: raw.storeName,
    role: raw.role || raw.position,
    employmentType: raw.employmentType || '全职',
    entryDate: raw.entryDate || raw.hireDate,
    status: raw.status || '在职',
  }
}

async function fetchDetail() {
  try {
    const res = (await getEmployeeDetail(route.params.id as string)) as any
    if (res.data) {
      employee.value = normalizeDetail(res.data)
      permissions.value = res.data.permissions || permissions.value
      timeline.value = res.data.timeline || timeline.value
      histories.value = res.data.histories || histories.value
    }
  } catch {
    // 接口未上线时保留 PRD 样例数据。
  }
}

onMounted(fetchDetail)
</script>

<style scoped>
.people-page {
  max-width: 1280px;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.detail-title-block {
  flex: 1;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #111827;
}

.page-subtitle {
  margin: 6px 0 0;
  color: #6b7280;
}

.section-card {
  margin-bottom: 16px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.info-label {
  color: #6b7280;
  font-size: 12px;
}

.info-value {
  margin-top: 4px;
  color: #111827;
  font-weight: 600;
}

.permission-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.permission-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border: 1px solid #eef1f3;
  border-radius: 8px;
  background: #fbfcfb;
}

.timeline-date,
.timeline-desc {
  color: #6b7280;
  font-size: 12px;
}

.history-list {
  display: grid;
  gap: 12px;
}

.history-item {
  display: grid;
  gap: 4px;
  padding: 12px;
  border-radius: 8px;
  background: #f9fafb;
}

.history-item span,
.history-item em {
  color: #6b7280;
  font-size: 12px;
  font-style: normal;
}
</style>
