package com.ngockhanh.clinic.patient.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.patient.infrastructure.persistence.view.PatientAccountOwnerView;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Patient reads for account creation by access control (UC-005). */
@Mapper
public interface PatientAccountOwnerMyBatisMapper {
  PatientAccountOwnerView lockById(@Param("id") UUID id);

  List<PatientAccountOwnerView> searchActive(
      @Param("keyword") String keyword,
      @Param("pattern") String pattern,
      @Param("limit") Integer limit);
}
