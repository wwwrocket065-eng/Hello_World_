# Protected updater rule

Do not change the local update discovery mechanism, its permissions, patch signing/verification, or rollback as part of unrelated UI/model work. Before any necessary updater modification, obtain explicit user approval and preserve the confirmed Android behavior. CI and real-device validation are required before declaring the release working.
