------------------------- MODULE GenIDTests -------------------------
EXTENDS GenID, TLC

ASSUME LET T == INSTANCE TLC IN T!PrintT("GenIDTests")

TEST ==
	SetID(10)
	
ASSUME LET T == INSTANCE TLC IN T!PrintT(NextID)
ASSUME LET T == INSTANCE TLC IN T!PrintT(GetID)


=============================================================================
