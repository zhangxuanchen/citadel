package cn.com.app.security.repository;

import cn.com.app.security.domain.ClientApp;
import java.util.List;
import java.util.Optional;
import tk.mybatis.mapper.common.Mapper;

public interface ClientAppRepository extends Mapper<ClientApp> {

    default Optional<ClientApp> findByCode(String code) {
        ClientApp probe = new ClientApp();
        probe.setCode(code);
        return Optional.ofNullable(selectOne(probe));
    }

    default Optional<ClientApp> findById(Long id) {
        return Optional.ofNullable(selectByPrimaryKey(id));
    }

    default boolean existsByCode(String code) {
        return findByCode(code).isPresent();
    }

    default List<ClientApp> findAll() {
        return selectAll();
    }

    @Override
    default int save(ClientApp clientApp) {
        if (clientApp.getId() == null) {
            return insertSelective(clientApp);
        }
        return updateByPrimaryKeySelective(clientApp);
    }

    @Override
    default int delete(ClientApp clientApp) {
        return deleteByPrimaryKey(clientApp.getId());
    }
}
