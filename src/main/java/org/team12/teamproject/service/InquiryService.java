package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.dto.InquiryCreateRequestDto;
import org.team12.teamproject.entity.Inquiry;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.repository.InquiryRepository;
import org.team12.teamproject.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long createInquiry(InquiryCreateRequestDto requestDto, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Inquiry inquiry = Inquiry.builder()
                .user(user)
                .category(requestDto.getCategory())
                .title(requestDto.getTitle())
                .content(requestDto.getContent())
                .build();

        return inquiryRepository.save(inquiry).getId();
    }
}
