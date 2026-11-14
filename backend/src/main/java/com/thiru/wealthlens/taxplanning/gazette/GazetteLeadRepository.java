package com.thiru.wealthlens.taxplanning.gazette;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface GazetteLeadRepository extends MongoRepository<GazetteLead, String> {

    List<GazetteLead> findAllByOrderByFoundAtDesc();
}
