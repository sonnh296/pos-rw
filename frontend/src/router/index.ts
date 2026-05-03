import { createRouter, createWebHashHistory } from 'vue-router'
import UsersPage from '../views/UsersPage.vue'
import CustomTestsDemo from '../views/CustomTestsDemo.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/users' },
    { path: '/users', component: UsersPage },
    { path: '/custom-tests', component: CustomTestsDemo },
  ],
})

export default router
