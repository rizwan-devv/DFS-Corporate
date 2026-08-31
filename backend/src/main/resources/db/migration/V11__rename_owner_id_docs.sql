-- Relabel owner ID docs (authorized-person wording removed from product UX)
UPDATE required_documents
SET document_label = 'Owner / account holder ID — Front'
WHERE document_code = 'AUTH_ID_FRONT';

UPDATE required_documents
SET document_label = 'Owner / account holder ID — Back'
WHERE document_code = 'AUTH_ID_BACK';

UPDATE required_documents
SET document_label = 'Owner / account holder live photo'
WHERE document_code = 'AUTH_LIVE_PHOTO';
