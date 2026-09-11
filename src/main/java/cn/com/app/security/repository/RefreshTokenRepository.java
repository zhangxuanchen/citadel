package cn.com.app.security.repository;

import cn.com.app.security.domain.RefreshToken;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

public interface RefreshTokenRepository extends Mapper<RefreshToken> {

    default Optional<RefreshToken> findByToken(String token) {
        RefreshToken probe = new RefreshToken();
        probe.setToken(token);
        return Optional.ofNullable(selectOne(probe));
    }

    @Override
    default int save(RefreshToken refreshToken) {
        if (refreshToken.getToken() == null) {
            throw new IllegalArgumentException("Refresh token value must not be null");
        }
        if (refreshToken.getId() == null) {
            return insertSelective(refreshToken);
        }
        return updateByPrimaryKeySelective(refreshToken);
    }

    @Delete("delete from sys_refresh_token where user_id = #{userId}")
    void deleteByUserId(@Param("userId") Long userId);
}
