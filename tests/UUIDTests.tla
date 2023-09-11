------------------------- MODULE UUIDTests -------------------------
EXTENDS UUID, TLC

ASSUME LET T == INSTANCE TLC IN T!PrintT("UUIDTests")

ASSUME LET T == INSTANCE TLC IN T!PrintT(UUID)


=============================================================================
