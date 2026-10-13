package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view.StaffAccountOwnerView;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view.StaffOwnerCandidateView;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Staff member reads of account creation (UC-005). */
@Mapper
public interface StaffMemberMyBatisMapper {
  StaffAccountOwnerView lockById(@Param("id") UUID id);

  List<StaffOwnerCandidateView> searchActiveWithoutAccount(
      @Param("pattern") String pattern, @Param("limit") Integer limit);
}
