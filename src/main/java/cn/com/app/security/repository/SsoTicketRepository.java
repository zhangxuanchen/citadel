package cn.com.app.security.repository;

import cn.com.app.security.domain.SsoTicket;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

public interface SsoTicketRepository extends Mapper<SsoTicket> {

    default Optional<SsoTicket> findByTicket(String ticket) {
        SsoTicket probe = new SsoTicket();
        probe.setTicket(ticket);
        return Optional.ofNullable(selectOne(probe));
    }

    @Override
    default int save(SsoTicket ssoTicket) {
        if (ssoTicket.getId() == null) {
            return insertSelective(ssoTicket);
        }
        return updateByPrimaryKeySelective(ssoTicket);
    }

    @Delete("delete from sys_sso_ticket where user_id = #{userId}")
    void deleteByUserId(@Param("userId") Long userId);
}
