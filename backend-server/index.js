// backend-server/index.js
const express = require('express');
const admin = require('firebase-admin');
const axios = require('axios'); // 네이버 토큰 유효성 검증을 위해 필요

// Firebase 서비스 계정 키 경로 (방금 다운로드한 파일 경로로 변경하세요!)
const serviceAccount = require('./serviceAccountKey.json'); // 예: './serviceAccountKey.json'

// Firebase Admin SDK 초기화
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const app = express();
app.use(express.json()); // JSON 요청 본문 파싱을 위한 미들웨어

const PORT = process.env.PORT || 3000;

// 네이버 액세스 토큰을 받아 Firebase 커스텀 토큰을 생성하는 엔드포인트
app.post('/verifyNaverToken', async (req, res) => {
    const naverAccessToken = req.body.accessToken;

    if (!naverAccessToken) {
        return res.status(400).send('네이버 액세스 토큰이 필요합니다.');
    }

    try {
        // 1. 네이버 액세스 토큰 유효성 검증 (선택 사항이지만 강력 권장)
        // 네이버 개발자 센터에서 '회원 프로필 조회' API 문서 참고
        const naverProfileResponse = await axios.get('https://openapi.naver.com/v1/nid/me', {
            headers: {
                'Authorization': `Bearer ${naverAccessToken}`
            }
        });

        // 네이버 사용자 고유 ID (네이버 API 응답에서 사용자 ID를 추출)
        const naverUserId = naverProfileResponse.data.response.id;

        if (!naverUserId) {
            return res.status(401).send('네이버 토큰이 유효하지 않거나 사용자 ID를 찾을 수 없습니다.');
        }

        // 2. Firebase 커스텀 토큰 생성
        // 네이버 사용자 ID를 Firebase UID로 사용하여 커스텀 토큰 생성
        const firebaseCustomToken = await admin.auth().createCustomToken(naverUserId);

        res.status(200).json({ firebaseToken: firebaseCustomToken });

    } catch (error) {
        console.error('네이버 토큰 검증 또는 Firebase 커스텀 토큰 생성 중 오류:', error.message);
        if (error.response) {
            console.error('네이버 API 응답 오류:', error.response.data);
        }
        res.status(500).send('서버 오류 발생');
    }
});

app.listen(PORT, () => {
    console.log(`백엔드 서버가 http://localhost:${PORT} 에서 실행 중입니다.`);
});