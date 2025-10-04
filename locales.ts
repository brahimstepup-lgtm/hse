export const translations = {
  ar: {
    // Header
    headerTitle: 'مساعد مسؤول السلامة والصحة المهنية (HSE)',
    headerSubtitle: 'أداة ذكية لتسجيل وتحليل مخاطر بيئة العمل',
    // Language Switcher
    language: 'اللغة',
    // Report Form
    formTitle: 'تسجيل حالة جديدة',
    formImageLabel: '1. صورة الحالة',
    formImagePrompt: 'انقر للإرفاق أو اسحب الصورة هنا',
    formImageMaxSize: '(الحد الأقصى 4 ميجابايت)',
    formImageAlt: 'معاينة',
    formDescriptionLabel: '2. وصف الحالة',
    formDescriptionPlaceholder: 'اكتب وصفاً موجزاً للمشكلة أو الحالة المرصودة...',
    formLocationLabel: '3. مكان الحالة',
    formLocationPlaceholder: 'مثال: قسم التجميع، خط الإنتاج رقم 5',
    formSubmitButton: 'تحليل وإنشاء تقرير',
    formSubmittingButton: 'جاري التحليل...',
    formErrorAllFields: 'يرجى ملء جميع الحقول وإرفاق صورة.',
    formErrorImageSize: 'حجم الصورة يجب أن يكون أقل من 4 ميجابايت.',
    // Report Table
    tableTitle: 'سجل الحالات',
    tableEmpty: 'لم يتم تسجيل أي حالات بعد. استخدم النموذج لبدء إنشاء التقارير.',
    tableHeaderImage: 'الصورة',
    tableHeaderDate: 'التاريخ',
    tableHeaderLocation: 'المكان',
    tableHeaderHazard: 'نوع الخطر',
    tableHeaderSeverity: 'درجة الخطورة',
    tableHeaderSolution: 'الحل المقترح',
    tableHeaderResponsible: 'المسؤول',
    tableHeaderActions: 'الإجراءات',
    // Edit Modal
    editModalTitle: 'تعديل التقرير',
    editModalLocationLabel: 'المكان',
    editModalHazardLabel: 'نوع الخطر',
    editModalSeverityLabel: 'درجة الخطورة',
    editModalSolutionLabel: 'الحل المقترح',
    editModalResponsibleLabel: 'المسؤول/القسم المسؤول',
    editModalSaveButton: 'حفظ التغييرات',
    editModalCancelButton: 'إلغاء',
    // Image Modal
    imageModalClose: 'إغلاق',
    // Severity levels
    severity_low: 'منخفضة',
    severity_medium: 'متوسطة',
    severity_high: 'عالية',
    severity_critical: 'حرجة',
    // Other
    actionEdit: 'تعديل',
    errorAnalysis: 'حدث خطأ أثناء تحليل الحالة. يرجى المحاولة مرة أخرى.',
  },
  fr: {
    // Header
    headerTitle: 'Assistant Responsable HSE',
    headerSubtitle: 'Outil intelligent pour enregistrer et analyser les risques professionnels',
    // Language Switcher
    language: 'Langue',
    // Report Form
    formTitle: 'Enregistrer un nouveau cas',
    formImageLabel: '1. Photo du cas',
    formImagePrompt: 'Cliquez pour joindre ou glissez-déposez une image ici',
    formImageMaxSize: '(Max 4 Mo)',
    formImageAlt: 'Aperçu',
    formDescriptionLabel: '2. Description du cas',
    formDescriptionPlaceholder: 'Rédigez une brève description du problème ou de la situation observée...',
    formLocationLabel: '3. Emplacement du cas',
    formLocationPlaceholder: 'Ex: Département d\'assemblage, ligne de production 5',
    formSubmitButton: 'Analyser et créer un rapport',
    formSubmittingButton: 'Analyse en cours...',
    formErrorAllFields: 'Veuillez remplir tous les champs et joindre une image.',
    formErrorImageSize: 'La taille de l\'image doit être inférieure à 4 Mo.',
    // Report Table
    tableTitle: 'Journal des cas',
    tableEmpty: 'Aucun cas n\'a encore été enregistré. Utilisez le formulaire pour commencer à créer des rapports.',
    tableHeaderImage: 'Image',
    tableHeaderDate: 'Date',
    tableHeaderLocation: 'Emplacement',
    tableHeaderHazard: 'Type de danger',
    tableHeaderSeverity: 'Niveau de gravité',
    tableHeaderSolution: 'Solution proposée',
    tableHeaderResponsible: 'Responsable',
    tableHeaderActions: 'Actions',
    // Edit Modal
    editModalTitle: 'Modifier le rapport',
    editModalLocationLabel: 'Emplacement',
    editModalHazardLabel: 'Type de danger',
    editModalSeverityLabel: 'Niveau de gravité',
    editModalSolutionLabel: 'Solution proposée',
    editModalResponsibleLabel: 'Responsable/Département',
    editModalSaveButton: 'Enregistrer les modifications',
    editModalCancelButton: 'Annuler',
    // Image Modal
    imageModalClose: 'Fermer',
    // Severity levels
    severity_low: 'Faible',
    severity_medium: 'Moyenne',
    severity_high: 'Élevée',
    severity_critical: 'Critique',
    // Other
    actionEdit: 'Modifier',
    errorAnalysis: 'Une erreur s\'est produite lors de l\'analyse du cas. Veuillez réessayer.',
  },
};

export type Language = keyof typeof translations;
export type TranslationKey = keyof typeof translations['ar'];