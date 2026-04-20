import { createRouter, createWebHashHistory } from 'vue-router'
import UsersPage from '../views/UsersPage.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/users' },
    { path: '/users', component: UsersPage },
  ],
})

export default router
