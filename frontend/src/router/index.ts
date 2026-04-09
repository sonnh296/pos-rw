import { createRouter, createWebHashHistory } from 'vue-router'
import RewardsDemo from '../views/RewardsDemo.vue'
import UsersPage from '../views/UsersPage.vue'
import LoadTestDemo from '../views/LoadTestDemo.vue'
import HistoryPage from '../views/HistoryPage.vue'
import BatchPage from '../views/BatchPage.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/load-test' },
    { path: '/load-test', component: LoadTestDemo },
    { path: '/rewards', component: RewardsDemo },
    { path: '/users', component: UsersPage },
    { path: '/history', component: HistoryPage },
    { path: '/batch', component: BatchPage },
  ],
})

export default router
