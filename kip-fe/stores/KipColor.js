import { defineStore } from 'pinia';
// 색상 공통화
export default defineStore("KipColors",{
    state: () => ({
        kipMainColor: '#2D3250',
        kipSubColor: '#7077A1',
        kipSheetColor: '#eeeeee',
    }),
});