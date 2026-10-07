<script setup lang="ts">defineProps<{ title: string }>()</script>
<template><header class="page-header"><div><h1>{{ title }}</h1></div><div class="page-header__actions"><slot /></div></header></template>
