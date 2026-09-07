package com.templestamp.manuscript.dto;

/**
 * CSV 반입에서 걸린 행 하나. 행 번호는 <b>사람이 엑셀에서 보는 번호</b>다(머리글이 1행).
 * 어느 열이 왜 틀렸는지를 함께 주지 않으면, 140행짜리 파일에서 무엇을 고칠지 알 수 없다.
 */
public record ImportError(int row, String field, String reason) {
}
