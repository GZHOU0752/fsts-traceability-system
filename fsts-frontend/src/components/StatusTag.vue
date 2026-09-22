<script setup lang="ts">withDefaults(defineProps<{ label?: string; tone?: 'success' | 'warning' | 'danger' | 'info' }>(), { label: '-', tone: 'info' })</script>
<template><el-tag class="status-tag" :type="tone" effect="light" round>{{ label }}</el-tag></template>
